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
import coinffeine.peer.payment.okpay.profile.{OkPayProfileConfigurator, ScrappingProfile}
import coinffeine.peer.payment.okpay.{OkPayApiCredentials, OkPaySettings}

class OkPaySeedTokenRetrievalPane(data: SetupConfig, stepNumber: Int)
  extends StepPane[SetupConfig] with LazyLogging {

  import OkPaySeedTokenRetrievalPane._

  override val icon = GlyphIcon.Numbers(stepNumber)

  private val title = new Label("OKPay API token") { styleClass += "title" }

  private val retrievalStatus =
    new ObjectProperty[RetrievalStatus](this, "retrievalStatus", InProgress)
  private val progressHint = new Label() {
    text <== retrievalStatus.delegate.mapToString(_.hint)
  }
  private val progressBar = new ProgressBar() {
    progress <== retrievalStatus.delegate.mapToDouble(_.progress)
    retrievalStatus.delegate.bindToList(styleClass) { status =>
      Seq("progress-bar") ++ status.failed.option("error")
    }
  }

  private val progressPane = new VBox {
    styleClass += "data"
    children = Seq(progressHint, progressBar)
  }

  private val subtitle = new HBox {
    visible <== retrievalStatus.delegate.mapToBool(_.failed)
    styleClass += "subtitle"
    children = Seq(
      new Label("You can configure manually your API credentials"),
      new SupportWidget("setup-credentials")
    )
  }

  private val accountIdField, seedTokenField = new TextField
  private val manualInputPane = new VBox {
    visible <== retrievalStatus.delegate.mapToBool(_.failed)
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
    retrievalStatus.delegate.zip(accountIdField.text, seedTokenField.text) {
      (status, manualId, manualToken) =>
        val manualCredentials = OkPayApiCredentials(manualId.trim, manualToken.trim)
        status match {
          case SuccessfulRetrieval(accessData) => Some(accessData)
          case FailedRetrieval(_) => Some(manualCredentials)
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
    retrievalStatus.set(InProgress)
    configureProfile(data.okPayCredentials.value).onComplete {
      case Success(accessData) => retrievalStatus.set(SuccessfulRetrieval(accessData))
      case Failure(ex) =>
        logger.error("Cannot configure OKPay profile and retrieve API credentials", ex)
        retrievalStatus.set(FailedRetrieval(ex))
    }
  }

  private def configureProfile(credentials: OkPayCredentials): Future[OkPayApiCredentials] = {
    implicit val context = scala.concurrent.ExecutionContext.global
    for {
      profile <- ScrappingProfile.login(credentials.id, credentials.password)
      configurator = new OkPayProfileConfigurator(profile)
      accessData <- configurator.configure()
    } yield accessData
  }

  private def validApiCredentials(credentials: OkPayApiCredentials): Boolean =
    credentials.walletId.matches(OkPaySettings.AccountIdPattern) &&
      credentials.seedToken.nonEmpty
}

object OkPaySeedTokenRetrievalPane {
  sealed trait RetrievalStatus {
    def hint: String
    def progress: Double
    def failed: Boolean
  }

  case object InProgress extends RetrievalStatus {
    override def hint = "Obtaining the token (this may take a while)..."
    override def progress = -1
    override def failed = false
  }

  case class FailedRetrieval(exception: Throwable) extends RetrievalStatus {
    override def hint = "Something went wrong while retrieving the token."
    override def progress = 1
    override def failed = true
  }

  case class SuccessfulRetrieval(accessData: OkPayApiCredentials) extends RetrievalStatus {
    override def hint = "Token retrieved successfully."
    override def progress = 1
    override def failed = false
  }
}
