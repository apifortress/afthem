package com.apifortress.afthem.config

import com.apifortress.afthem.config.loaders.YamlConfigLoader

object ConfigLoader extends TConfigLoader {

  val implementer = Class.forName(new YamlConfigLoader().loadAfthemRootConf().config_loader_class).newInstance().asInstanceOf[TConfigLoader]

  override def loadBackends(): Backends = return implementer.loadBackends()

  override def loadFlow(id: String): Flow = return implementer.loadFlow(id)

  override def loadImplementers(): Implementers = return implementer.loadImplementers()
}
