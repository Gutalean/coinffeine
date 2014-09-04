package coinffeine.peer.config

/** Cake-pattern provider of configurations */
trait ConfigComponent {

  def settingsProvider: SettingsProvider
}
