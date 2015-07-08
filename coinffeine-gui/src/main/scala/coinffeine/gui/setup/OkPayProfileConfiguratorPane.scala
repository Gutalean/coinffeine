package coinffeine.gui.setup

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scalafx.beans.property.ObjectProperty
import scalafx.scene.control._
import scalafx.scene.layout.{HBox, VBox}
import scalaz.syntax.std.boolean._

import com.typesafe.scalalogging.LazyLogging

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.{GlyphIcon, SupportWidget}
import coinffeine.gui.util.FxExecutor
import coinffeine.gui.wizard.{StepPane, StepPaneEvent}
import coinffeine.peer.payment.okpay.profile.{ProfileConfigurator, ScrappingProfile}
import coinffeine.peer.payment.okpay.{OkPayApiCredentials, OkPaySettings}

class OkPayProfileConfiguratorPane(data: SetupConfig, stepNumber: Int)
  extends StepPane[SetupConfig] with LazyLogging {

  import OkPayProfileConfiguratorPane._

  override val icon = GlyphIcon.Numbers(stepNumber)

  private val title = new Label("OKPay API token") { styleClass += "title" }

  private val configurationStatus =
    new ObjectProperty[ConfigurationStatus](this, "configurationStatus", InProgress)
  private val progressHint = new Label() {
    text <== configurationStatus.delegate.mapToString(_.hint)
  }
  private val progressBar = new ProgressBar() {
    progress <== configurationStatus.delegate.mapToDouble(_.progress)
    configurationStatus.delegate.bindToList(styleClass) { status =>
      Seq("progress-bar") ++ status.failed.option("error")
    }
  }

  private val progressPane = new VBox {
    styleClass += "data"
    children = Seq(progressHint, progressBar)
  }

  private val subtitle = new HBox {
    visible <== configurationStatus.delegate.mapToBool(_.failed)
    styleClass += "subtitle"
    children = Seq(
      new Label("You can configure manually your API credentials"),
      new SupportWidget("setup-credentials")
    )
  }

  private val accountIdField, seedTokenField = new TextField
  private val manualInputPane = new VBox {
    visible <== configurationStatus.delegate.mapToBool(_.failed)
    styleClass += "data"
    children = Seq(
      new Label("Your account ID"),
      accountIdField,
      new Label("Your token"),
      seedTokenField
    )
  }

  children = new VBox {
    styleClass += "okpay-pane"
    children = Seq(title, progressPane, subtitle, manualInputPane)
  }

  data.okPayWalletAccess <==
    configurationStatus.delegate.zip(accountIdField.text, seedTokenField.text) {
      (status, manualId, manualToken) =>
        val manualCredentials = OkPayApiCredentials(manualId.trim, manualToken.trim)
        status match {
          case SuccessfulConfiguration(result) => Some(result.credentials)
          case FailedConfiguration(_) => Some(manualCredentials)
          case _ => None
        }
    }

  canContinue <== data.okPayWalletAccess.delegate.mapToBool {
    case Some(credentials) => validApiCredentials(credentials)
    case _ => false
  }

  onActivation = (e: StepPaneEvent) => startTokenRetrieval()

  private def startTokenRetrieval(): Unit = {
    implicit val context = FxExecutor.asContext
    configurationStatus.set(InProgress)
    configureProfile(data.okPayCredentials.value).onComplete {
      case Success(accessData) => configurationStatus.set(SuccessfulConfiguration(accessData))
      case Failure(ex) =>
        logger.error("Cannot configure OKPay profile and retrieve API credentials", ex)
        configurationStatus.set(FailedConfiguration(ex))
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
    def hint: String
    def progress: Double
    def failed: Boolean
  }

  case object InProgress extends ConfigurationStatus {
    override def hint = "Obtaining the token (this may take a while)..."
    override def progress = -1
    override def failed = false
  }

  case class FailedConfiguration(exception: Throwable) extends ConfigurationStatus {
    override def hint = "Something went wrong while retrieving the token."
    override def progress = 1
    override def failed = true
  }

  case class SuccessfulConfiguration(result: ProfileConfigurator.Result)
      extends ConfigurationStatus {
    override def hint = "Token retrieved successfully."
    override def progress = 1
    override def failed = false
  }
}
