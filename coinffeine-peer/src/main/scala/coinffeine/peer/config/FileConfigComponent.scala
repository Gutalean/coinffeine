package coinffeine.peer.config

trait FileConfigComponent extends ConfigComponent {

  def configProvider = FileConfigProvider()
}
