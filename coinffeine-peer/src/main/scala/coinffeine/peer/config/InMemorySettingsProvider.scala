package coinffeine.peer.config

import com.typesafe.config.Config

class InMemorySettingsProvider(val userConfig: Config) extends SettingsProvider
