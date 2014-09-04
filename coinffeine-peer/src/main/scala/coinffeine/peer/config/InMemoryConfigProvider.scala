package coinffeine.peer.config

import com.typesafe.config.Config

class InMemoryConfigProvider(val userConfig: Config) extends ConfigProvider
