package coinffeine.gui.setup

import scala.util.control.NonFatal
import scala.util.{Failure, Success}
import scalafx.beans.property.ObjectProperty
import scalafx.event.ActionEvent
import scalafx.event.EventIncludes._
import scalafx.geometry.HPos
import scalafx.scene.control.{Button, Label, ProgressBar}
import scalafx.scene.layout._

import org.controlsfx.dialog.{DialogStyle, Dialogs}

import coinffeine.gui.control.GlyphIcon
import coinffeine.gui.util.FxExecutor
import coinffeine.gui.wizard.StepPane
import coinffeine.peer.payment.okpay.{OkPayWalletAccess, OkPayProfileExtractor}

private[setup] class OkPaySeedTokenRetrievalPane extends StackPane with StepPane[SetupConfig] {

  override val icon = GlyphIcon.Coinffeine

  val retrievalError = new ObjectProperty[Throwable](null, "error")

  val progressHint = new Label()
  val retrievalProgress = new ProgressBar() { maxWidth = Double.MaxValue }
  val errorLine = new GridPane() {
    id = "wizard-okpaytoken-errorline-pane"
    columnConstraints = Seq(
      new ColumnConstraints() { halignment = HPos.Left },
      new ColumnConstraints() { halignment = HPos.Right }
    )
    visible <== retrievalError.isNotNull

    add(new Label("Please go back and double check your credentials.") {
      styleClass.add("wizard-error-label")
      hgrow = Priority.Always
    }, 0, 0)
    add(new Button("Details") {
      onAction = { (_: ActionEvent) =>
        Dialogs.create()
          .style(DialogStyle.NATIVE)
          .title("Token retrieval error")
          .showException(retrievalError.value)
      }
    }, 1, 0)
  }

  def initFields(): Unit = {
    progressHint.text = "Obtaining a token for your OKPay account (this may take a while)..."
    retrievalProgress.progress = -1.0f
    retrievalProgress.styleClass.removeAll("wizard-error-progressbar")
    retrievalError.value = null
  }

  def reportError(error: Throwable): Unit = {
    progressHint.text = "Something went wrong while retrieving the token."
    retrievalProgress.styleClass.add("wizard-error-progressbar")
    retrievalProgress.progress = 1.0f
    retrievalError.value = error
  }

  override def bindTo(data: ObjectProperty[SetupConfig]) = {
    implicit val context = FxExecutor.asContext
    initFields()
    try {
      val credentials = data.value.okPayCredentials.get
      val extractor = new OkPayProfileExtractor(credentials.id, credentials.password)
      extractor.configureProfile().onComplete {
        case Success(profile) =>
          progressHint.text = "Token retrieved successfully."
          retrievalProgress.progress = 1.0f
          data.value.okPayWalletAccess.value = OkPayWalletAccess(profile.walletId, profile.token)
          canContinue.value = true
        case Failure(error) =>
          reportError(error)
      }
    } catch {
      case NonFatal(error) => reportError(error)
    }
  }

  content = new VBox(spacing = 5) {
    id = "wizard-okpaytoken-base-pane"
    content = Seq(
      progressHint,
      retrievalProgress,
      errorLine
    )
  }
}
