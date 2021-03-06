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
package com.apifortress.afthem.actors.transformers

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestActorRef, TestProbe}
import com.apifortress.afthem.TestData
import com.apifortress.afthem.actors.filters.DelayActor
import com.apifortress.afthem.config.Phase
import com.apifortress.afthem.exceptions.AfthemFlowException
import com.apifortress.afthem.messages.{BaseMessage, WebParsedRequestMessage, WebParsedResponseMessage}
import org.junit.Assert._
import org.junit.Test
import org.slf4j.LoggerFactory

import scala.concurrent.duration._


class BeautifyPayloadActorTests {

  @Test
  def testBeautificationJson() : Unit = {
    val data = "{\"foo\":\"bar\"}"
    val result = BeautifyPayloadActor.beautify(data.getBytes,"json",null)
    assertEquals("{\n  \"foo\" : \"bar\"\n}",new String(result))
  }

  @Test
  def testBeautificationNoJson() : Unit = {
    val data = "foo\nbar"
    val log = LoggerFactory.getLogger(this.getClass)
    val result = BeautifyPayloadActor.beautify(data.getBytes,"json",log)
    assertEquals("foo\nbar",new String(result))
  }

  @Test
  def testBeautificationXml() : Unit = {
    val data = "<root attr=\"attr1\"><foo>bar</foo></root>"
    val result = BeautifyPayloadActor.beautify(data.getBytes,"xml",null)
    assertEquals("<root attr=\"attr1\">\n    <foo>bar</foo>\n</root>", new String(result))
  }

  @Test
  def testBeautificationNoXml() : Unit = {
    val data = "foo\nbar"
    val log = LoggerFactory.getLogger(this.getClass)
    val result = BeautifyPayloadActor.beautify(data.getBytes,"xml",log)
    assertEquals("foo\nbar",new String(result))
  }


  @Test
  def testActor() : Unit = {
    implicit val system = ActorSystem()
    val probe = TestProbe()
    val actor = system.actorOf(Props(new BeautifyPayloadActor("abc") {
      override def forward(msg: BaseMessage): Unit = {
        probe.ref ! msg
      }

      override def getPhase(message: BaseMessage): Phase = {
        return new Phase("abc","next")
      }
    }))
    val wrapper = TestData.createWrapper()
    wrapper.payload = "{\"foo\":\":bar\"}".getBytes
    actor ! new WebParsedResponseMessage(wrapper,null,null,null,null)
    val response = probe.expectMsgClass(5 seconds, classOf[WebParsedResponseMessage])
    assertEquals("{\n  \"foo\" : \":bar\"\n}",new String(response.response.payload))

    actor ! new WebParsedRequestMessage(wrapper,null,null,null)
    val response2 = probe.expectMsgClass(5 seconds, classOf[WebParsedRequestMessage])
    assertEquals("{\n  \"foo\" : \":bar\"\n}",new String(response2.request.payload))
    Thread.sleep(500)
    system.terminate()
  }

  @Test(expected = classOf[AfthemFlowException])
  def testActorFail() : Unit = {
    implicit val system = ActorSystem()
    val start = System.currentTimeMillis()
    val actor = TestActorRef(new BeautifyPayloadActor("abc") {
      override def getPhase(message: BaseMessage): Phase = {
        new Phase("abc","next",List.empty[String],Map.empty)
      }
    })
    actor.receive(new WebParsedRequestMessage(TestData.createWrapper(),null,null,null))

    system.terminate()
  }
}
