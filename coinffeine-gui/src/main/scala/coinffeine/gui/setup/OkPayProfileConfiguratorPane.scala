package coinffeine.gui.setup

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scalafx.beans.property.ObjectProperty
import scalafx.scene.control._
import scalafx.scene.layout.{HBox, StackPane, VBox}
import scalaz.syntax.std.boolean._

import com.typesafe.scalalogging.LazyLogging

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.{GlyphIcon, SupportWidget}
import coinffeine.gui.util.FxExecutor
import coinffeine.gui.wizard.{StepPane, StepPaneEvent}
import coinffeine.model.payment.okpay.VerificationStatus
import coinffeine.peer.payment.okpay.profile.{ProfileConfigurator, ScrappingProfile}
import coinffeine.peer.payment.okpay.{OkPayApiCredentials, OkPaySettings}

class OkPayProfileConfiguratorPane(data: SetupConfig, stepNumber: Int)
  extends StepPane[SetupConfig] with LazyLogging {

  import OkPayProfileConfiguratorPane._

  override val icon = GlyphIcon.Numbers(stepNumber)

  private val title = new Label("OKPay API token") { styleClass += "title" }

  private val automaticConfigStatus =
    new ObjectProperty[ConfigurationStatus](this, "configurationStatus", InProgress)

  private val progressPane = new VBox {
    styleClass += "data"
    visible <== automaticConfigStatus.delegate.mapToBool(!_.failed)
    children = Seq(
      new Label {
        text <== automaticConfigStatus.delegate.mapToString { status =>
          if (status.finished) "Token retrieved successfully."
          else "Obtaining the token (this may take a while)..."
        }
      },
      new ProgressBar() {
        progress <== automaticConfigStatus.delegate.mapToDouble { status =>
          if (status.finished) 1 else -1
        }
        automaticConfigStatus.delegate.bindToList(styleClass) { status =>
          Seq("progress-bar") ++ status.failed.option("error")
        }
      }
    )
  }

  private val subtitle = new HBox {
    visible <== automaticConfigStatus.delegate.mapToBool(_.failed)
    styleClass += "subtitle"
    children = Seq(
      new Label("You can manually configure your API credentials"),
      new SupportWidget("setup-credentials")
    )
  }

  private val accountIdField, seedTokenField = new TextField
  private val verificationStatusField = new CheckBox("Verified account")
  private val manualConfiguration =
    accountIdField.text.delegate.zip(seedTokenField.text, verificationStatusField.selected) {
      (accountId, seedToken, verified) =>
        ProfileConfigurator.Result(
          OkPayApiCredentials(accountId.trim, seedToken.trim),
          VerificationStatus.fromBoolean(verified)
        )
    }

  private val manualInputPane = new VBox {
    visible <== automaticConfigStatus.delegate.mapToBool(_.failed)
    styleClass += "data"
    children = Seq(
      subtitle,
      new Label("Your account ID"),
      accountIdField,
      new Label("Your token"),
      seedTokenField,
      verificationStatusField,
      new Label("Unverified accounts cannot transfer more than 300EUR/month") {
        styleClass += "explanation"
      }
    )
  }

  children = new VBox {
    styleClass += "okpay-pane"
    children = Seq(
      title,
      new StackPane {
        children = Seq(progressPane, manualInputPane)
      }
    )
  }

  private val mergedConfiguration = automaticConfigStatus.delegate.zip(manualConfiguration) {
    case (SuccessfulConfiguration(automaticConfig), _) => Some(automaticConfig)
    case (FailedConfiguration(_), manualConfig) => Some(manualConfig)
    case _ => None
  }

  data.okPayWalletAccess <== mergedConfiguration.map(_.map(_.credentials))
  data.okPayVerificationStatus <== mergedConfiguration.map(_.map(_.verificationStatus))

  canContinue <== data.okPayWalletAccess.delegate.mapToBool {
    case Some(credentials) => validApiCredentials(credentials)
    case _ => false
  }

  onActivation = (e: StepPaneEvent) => startTokenRetrieval()

  private def startTokenRetrieval(): Unit = {
    implicit val context = FxExecutor.asContext
    automaticConfigStatus.set(InProgress)
    configureProfile(data.okPayCredentials.value).onComplete {
      case Success(accessData) => automaticConfigStatus.set(SuccessfulConfiguration(accessData))
      case Failure(ex) =>
        logger.error("Cannot configure OKPay profile and retrieve API credentials", ex)
        automaticConfigStatus.set(FailedConfiguration(ex))
    }
  }

  private def configureProfile(
      credentials: OkPayCredentials): Future[ProfileConfigurator.Result] = {
    implicit val context = scala.concurrent.ExecutionContext.global
    for {
      profile <- ScrappingProfile.login(credentials.id, credentials.password)
      result <- new ProfileConfigurator(profile).configure()
    } yield result
  }

  private def validApiCredentials(credentials: OkPayApiCredentials): Boolean =
    credentials.walletId.matches(OkPaySettings.AccountIdPattern) &&
      credentials.seedToken.nonEmpty
}

object OkPayProfileConfiguratorPane {
  sealed trait ConfigurationStatus {
    def finished: Boolean
    def failed: Boolean
  }

  case object InProgress extends ConfigurationStatus {
    override def finished = false
    override def failed = false
  }

  case class FailedConfiguration(exception: Throwable) extends ConfigurationStatus {
    override def finished = true
    override def failed = true
  }

  case class SuccessfulConfiguration(result: ProfileConfigurator.Result)
      extends ConfigurationStatus {
    override def finished = true
    override def failed = false
  }
}
