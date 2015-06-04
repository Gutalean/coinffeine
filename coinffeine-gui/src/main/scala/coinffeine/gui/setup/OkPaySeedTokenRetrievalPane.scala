package coinffeine.gui.setup

import scala.util.{Failure, Success}
import scalafx.beans.property.ObjectProperty
import scalafx.scene.control._
import scalafx.scene.layout.{HBox, VBox}
import scalaz.syntax.std.boolean._

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.{GlyphIcon, SupportWidget}
import coinffeine.gui.util.FxExecutor
import coinffeine.gui.wizard.{StepPane, StepPaneEvent}
import coinffeine.peer.payment.okpay.OkPayApiCredentials
import coinffeine.peer.payment.okpay.profile.OkPayProfileConfigurator

class OkPaySeedTokenRetrievalPane(data: SetupConfig) extends StepPane[SetupConfig] {
  import OkPaySeedTokenRetrievalPane._

  override val icon = GlyphIcon.Number3

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

  private val subtitle = new HBox {
    styleClass += "subtitle"
    children = Seq(
      new Label("Configuring an OKPay API token for you"),
      new SupportWidget("setup-credentials")
    )
  }

  private val dataPane = new VBox {
    styleClass += "data"
    children = Seq(progressHint, progressBar)
  }

  children = new VBox {
    styleClass += "okpay-pane"
    children = Seq(title, subtitle, dataPane)
  }

  canContinue <== retrievalStatus.delegate.mapToBool(_ != InProgress)

  onActivation = (e: StepPaneEvent) => { startTokenRetrieval() }

  data.okPayWalletAccess <== retrievalStatus.delegate.map {
    case SuccessfulRetrieval(accessData) => Some(accessData)
    case _ => None
  }

  private def startTokenRetrieval(): Unit = {
    implicit val context = FxExecutor.asContext
    retrievalStatus.set(InProgress)
    val credentials = data.okPayCredentials.value
    val extractor = new OkPayProfileConfigurator(credentials.id, credentials.password)
    extractor.configure().onComplete {
      case Success(accessData) =>
        retrievalStatus.set(SuccessfulRetrieval(accessData))
      case Failure(ex) =>
        retrievalStatus.set(FailedRetrieval(ex))
    }
  }
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
    override def hint = "Something went wrong while retrieving the token. " +
      "You can continue but the account won't be configured"
    override def progress = 1
    override def failed = true
  }

  case class SuccessfulRetrieval(accessData: OkPayApiCredentials) extends RetrievalStatus {
    override def hint = "Token retrieved successfully"
    override def progress = 1
    override def failed = false
  }
}
