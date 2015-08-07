package coinffeine.gui.control

import scala.util.{Failure, Success}
import scalafx.Includes._
import scalafx.beans.property.ObjectProperty
import scalafx.scene.control.{Button, Label}
import scalafx.scene.layout.{Priority, StackPane, HBox}

import com.typesafe.scalalogging.LazyLogging

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.util.FxExecutor._
import coinffeine.peer.api.CoinffeinePaymentProcessor
import coinffeine.peer.api.CoinffeinePaymentProcessor.TestResult
import coinffeine.peer.payment.okpay.OkPayApiCredentials

class CredentialsTestWidget(paymentProcessor: CoinffeinePaymentProcessor)
    extends HBox(spacing = 5) with LazyLogging {

  import CredentialsTestWidget._

  private val _credentials =
    new ObjectProperty[OkPayApiCredentials](this, "credentials", OkPayApiCredentials.empty)
  def credentials: ObjectProperty[OkPayApiCredentials] = _credentials
  def credentials_=(value: OkPayApiCredentials): Unit = {
    _credentials.value = value
  }

  private val state = new ObjectProperty[State](this, "state", State.Idle)

  private val button = new Button("Test") {
    styleClass += "action-button"
    disable <== state.delegate.map(_.isBusy).toBool
        .or(_credentials.delegate.map(_ == OkPayApiCredentials.empty).toBool)
    onAction = startTesting _
  }

  private val spinner = new Spinner(autoPlay = true) {
    visible <== state.delegate.map(_.isBusy).toBool
  }

  private val description = new Label {
    styleClass += "description"
    text <== state.delegate.map(_.description)
  }

  styleClass += "credentials-test"
  children = new HBox {
    styleClass += "content"
    children = Seq(button, new StackPane() {
      styleClass += "results"
      hgrow = Priority.Always
      children = Seq(spinner, description)
    })
  }

  private def startTesting(): Unit = {
    val credentialsToTest = _credentials.value
    state.value = State.Testing
    paymentProcessor.testCredentials(credentialsToTest).onComplete {
      case Success(result) =>
        state.value = State.Tested(result)
      case Failure(ex) =>
        logger.error("Cannot test OK Pay credentials {}", credentialsToTest, ex)
        state.value = State.Tested(TestResult.CannotConnect)
    }
  }
}

private object CredentialsTestWidget {
  sealed trait State {
    def isBusy: Boolean = false
    def description: String = ""
  }

  object State {
    case object Idle extends State

    case object Testing extends State {
      override def isBusy = true
    }

    case class Tested(result: TestResult) extends State {
      override def description: String = result match {
        case TestResult.Valid => "OK"
        case TestResult.Invalid => "Invalid credentials"
        case TestResult.CannotConnect => "Couldn't connect"
      }
    }
  }
}
