package coinffeine.peer.config

trait FileConfigComponent extends ConfigComponent {

  def settingsProvider = FileSettingsProvider()
}
