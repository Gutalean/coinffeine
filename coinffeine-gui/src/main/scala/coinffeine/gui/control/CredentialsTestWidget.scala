package coinffeine.gui.control

import scala.util.{Failure, Success}
import scalafx.Includes._
import scalafx.beans.property.ObjectProperty
import scalafx.scene.control.Button
import scalafx.scene.layout.HBox

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

  private val button = new Button {
    styleClass += "action-button"

    text <== state.delegate.map(_.description).toStr

    disable <== _credentials.delegate.map(_ == OkPayApiCredentials.empty).toBool

    graphic <== state.delegate.map { state =>
      if (state.isBusy) spinner.delegate else null
    }

    onAction = () => {
      if (!state.value.isBusy) {
        startTesting()
      }
    }
  }

  private val spinner = new Spinner(autoPlay = true) {
    visible <== state.delegate.map(_.isBusy).toBool
  }

  styleClass += "credentials-test"
  children = Seq(button)

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
    def description: String
  }

  object State {
    case object Idle extends State {
      override def description = "Test"
    }

    case object Testing extends State {
      override def isBusy = true
      override def description = "Testing..."
    }

    case class Tested(result: TestResult) extends State {
      override def description: String = result match {
        case TestResult.Valid => "Valid, test again"
        case TestResult.Invalid => "Invalid credentials, test again"
        case TestResult.CannotConnect => "Couldn't connect, test again"
      }
    }
  }
}
