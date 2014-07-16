package com.coinffeine.gui

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Random
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage

import coinffeine.peer.api.impl.ProductionCoinffeineApp
import coinffeine.peer.payment.okpay.OkPayCredentials
import com.coinffeine.gui.application.ApplicationScene
import com.coinffeine.gui.application.main.MainView
import com.coinffeine.gui.application.operations.OperationsView
import com.coinffeine.gui.setup.CredentialsValidator.Result
import com.coinffeine.gui.setup.{CredentialsValidator, SetupWizard}

object Main extends JFXApp with ProductionCoinffeineApp.Component {

  JFXApp.AUTO_SHOW = false

  val validator = new CredentialsValidator {
    override def apply(credentials: OkPayCredentials): Future[Result] = Future {
      Thread.sleep(2000)
      if (Random.nextBoolean()) CredentialsValidator.ValidCredentials
      else CredentialsValidator.InvalidCredentials("Random failure")
    }
  }
  val sampleAddress = "124U4qQA7g33C4YDJFpwqXd2XJiA3N6Eb7"
  val setupConfig = new SetupWizard(sampleAddress, validator).run()

  Await.result(app.network.connect(), Duration.Inf)

  stage = new PrimaryStage {
    title = "Coinffeine"
    scene = new ApplicationScene(views = Seq(new MainView, new OperationsView(app)))
  }
  stage.show()


  override def stopApp(): Unit = {
    app.close()
  }
}
