package coinffeine.gui.application.launcher

import scala.concurrent.{ExecutionContext, Future}

import coinffeine.peer.config.ConfigProvider
import coinffeine.peer.log.LogConfigurator

class ConfigureLogAction(configProvider: ConfigProvider) {

  def apply(): Future[Unit] = {
    Future(LogConfigurator.configure(configProvider))(ExecutionContext.global)
  }
}
