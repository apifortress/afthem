/**
  * Copyright 2019 API Fortress
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  *
  * @author Simone Pezzano
  */
package com.apifortress.afthem.actors

import java.io.{File, FileReader}
import java.util.Date

import akka.actor.{ActorRef, ActorSystem, Props}
import com.apifortress.afthem.{AfthemHttpClient, Metric}
import com.apifortress.afthem.actors.probing.ProbeHttpActor
import com.apifortress.afthem.config.loaders.YamlConfigLoader
import com.apifortress.afthem.config.{AfthemCache, Implementers, RestartFlag}
import com.apifortress.afthem.messages.{StartActorsCommand, StartIngressesCommand}
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Application context. Initializes actors on load and provides useful methods to play with them
  */
object AppContext {

  val log = LoggerFactory.getLogger(AppContext.getClass)

  val DISPATCHER_DEFAULT = "default"
  val DISPATCHER_PROBE = "probe"

  var actorSystem : ActorSystem = null
  var probeHttpActor : ActorRef = null

  var initializationTime : Date = _

  /**
    * Spring application context
    */
  var springApplicationContext : ApplicationContext = null

  var restartFlag : RestartFlag = new RestartFlag(-1l,new Date())

  /**
    * Initializes the AppContext
    */
  def init() = {

    initializationTime = new Date()

    AfthemCache.clearAll()
    /**
      * Akka textual configuration
      */
    val cfg : StringBuffer = new StringBuffer()
    Implementers.instance.threadPools.foreach { pool =>
      cfg.append(s"${pool._1} {\n")
      cfg.append("\ttype = \"Dispatcher\"\n")
      cfg.append("\texecutor = \"fork-join-executor\"\n")
      cfg.append("\tfork-join-executor {\n")
      cfg.append(s"\t\tparallelism-min=${pool._2.min}\n")
      cfg.append(s"\t\tparallelism-max=${pool._2.max}\n")
      cfg.append(s"\t\tparallelism-factor=${pool._2.factor}\n")
      cfg.append("\t}\n")
      cfg.append("}\n")
    }
    val extraAkkaConfig = new File(YamlConfigLoader.SUBPATH+File.separator+"akka.conf")
    if(extraAkkaConfig.exists()){
      val reader = new FileReader(extraAkkaConfig)
      cfg.append(IOUtils.toString(reader))
      reader.close()
    }

    /**
      *
      * The Akka configuration
      */
    val config = ConfigFactory.parseString(cfg.toString)
    /**
      * The actor system
      */
    actorSystem = ActorSystem.create("afthem",config)

    implicit val executor = actorSystem.dispatchers.lookup(DISPATCHER_DEFAULT)

    /*
     * For each implementer, we create an actor.
     */
    val types : List[String] = Implementers.instance.implementers.map(item=> item.actorType).distinct
    types.foreach { actorType =>
      val supervisor = actorSystem.actorOf(Props.create(classOf[GenericSupervisorActor],actorType).withDispatcher(DISPATCHER_DEFAULT),actorType)
      supervisor ! StartActorsCommand(Implementers.instance.implementers.filter(item => item.actorType == actorType),config)
    }

    /* Stale connections are a problem with the Upstream HTTP Client. This is why we need a recurring task that will
     * check if connections need to be evicted.
     */
    actorSystem.scheduler.schedule(1 minute, 15 seconds, () => {
      AfthemHttpClient.closeStaleConnections()
    })

    actorSystem.scheduler.schedule(1 minute, 1 minute, () =>{
      checkForRestartTags()
    })

    val probeExecutorId = if (actorSystem.dispatchers.hasDispatcher(DISPATCHER_PROBE)) DISPATCHER_PROBE
    else DISPATCHER_DEFAULT

    /**
      * The actor that runs the probes for multiple upstreams
      */
    probeHttpActor = actorSystem.actorOf(Props[ProbeHttpActor].withDispatcher(probeExecutorId),"probeHttpActor")

    if( Implementers.instance.ingresses !=null ) {
        val supervisor = actorSystem.actorOf(Props.create(classOf[GenericSupervisorActor],"ingress").withDispatcher(DISPATCHER_DEFAULT),"ingress")
        supervisor ! new StartIngressesCommand(Implementers.instance.ingresses, config)
    }

  }


  /**
    * Inits the AppContext with the Spring application context
    * @param springApplicationContext a Spring application context
    */
  def init(springApplicationContext : ApplicationContext) : Unit = {
    this.springApplicationContext = springApplicationContext
    init()
  }

  /**
    * Reinitializes the AppContext
    */
  def reinit() : Unit = {
    log.info("Re-initializing system")
    val m = new Metric()
    val future = actorSystem.terminate()
    Await.result(future,1.minute)
    init()
    log.info("Re-initialization took: "+ m.toString())
  }

  private def checkForRestartTags(): Unit = {
    log.debug("Checking for context restart")
    val restartFlag = new YamlConfigLoader().loadRestarterFlag()
    if(this.restartFlag.version < restartFlag.version) {
      log.info("Commencing context restart")
      this.restartFlag = restartFlag
      reinit()
    }
  }



}
