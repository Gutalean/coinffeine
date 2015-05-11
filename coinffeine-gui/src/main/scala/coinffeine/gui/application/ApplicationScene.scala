package coinffeine.gui.application

import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}
import scalafx.event.ActionEvent
import scalafx.scene.Node
import scalafx.scene.control._
import scalafx.scene.layout._

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.PaymentProcessorWidget
import coinffeine.gui.preferences.PaymentProcessorSettingsForm
import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.{NodeStyles, PaneStyles, Stylesheets, TextStyles}
import coinffeine.model.currency._
import coinffeine.peer.config.SettingsProvider

/** Main scene of the application.
  *
  * @param balances The balances to be shown in the scene.
  * @param views  Available application views. The first one is visible at application start.
  * @param statusBarWidgets  Widgets displayed on the status bar at the bottom of the window
  * @param settingsProvider An object that provides the application settings
  */
class ApplicationScene(balances: ApplicationScene.Balances,
                       views: Seq[ApplicationView],
                       statusBarWidgets: Seq[Node],
                       settingsProvider: SettingsProvider) extends CoinffeineScene(
    Stylesheets.Operations, Stylesheets.Stats, Stylesheets.Wallet, Stylesheets.Alarms) {

  require(views.nonEmpty, "At least one view is required")

  private val currentView = new ObjectProperty[ApplicationView](this, "currentView", views.head)
  private val settingsForm = new PaymentProcessorSettingsForm(settingsProvider)

  private val viewSelector: Seq[ToggleButton] = {
    val group = new ToggleGroup
    val buttons = for (view <- views) yield new ToggleButton(view.name.toUpperCase) {
      disable <== selected
      handleEvent(ActionEvent.Action) { () => currentView.value = view }
    }
    buttons.foreach(b => b.toggleGroup = group)
    buttons.head.selected = true
    buttons
  }

  val topBar = new HBox {
    id = "top-bar"
    children = new PaymentProcessorWidget(settingsForm)
  }

  val balancePane = new VBox {
    id = "balance-pane"
    children = Seq(
      new HBox(
        new Label("AVAILABLE") { styleClass += "title-amount" },
        new Label("BALANCE") { styleClass += "title-suffix" }
      ) with PaneStyles.MinorSpacing,
      new HBox {
        styleClass += "currency"
        children = Seq(
          new Label with TextStyles.CurrencyAmount {
            text <== balances.fiat.delegate.mapToString {
              case Some(b) => b.amount.format(Currency.NoSymbol)
              case None => CurrencyAmount.formatMissing(Euro, Currency.NoSymbol)
            }
          },
          new Label("EUR") with TextStyles.CurrencySymbol)
      },
      new HBox {
        styleClass += "currency"
        children = Seq(
          new Label with TextStyles.CurrencyAmount {
            text <== balances.bitcoin.delegate.mapToString {
              case Some(b) => b.estimated.format(Currency.NoSymbol)
              case None => CurrencyAmount.formatMissing(Euro, Currency.NoSymbol)
            }
          },
          new Label("BTC") with TextStyles.CurrencySymbol)
      }
    )
  }

  val viewSelectorPane = new HBox with NodeStyles.HExpand {
    id = "view-selector-pane"
    children = viewSelector
  }

  val controlPane = new StackPane {
    id = "control-pane"
    currentView.delegate.bindToList(children) { p => Seq(p.controlPane) }
  }

  val controlBar = new HBox {
    id = "control-bar"
    children = Seq(balancePane, viewSelectorPane, controlPane)
  }

  private val statusBarPane = new HBox with PaneStyles.StatusBar {
    id = "status"
    prefHeight = 25
    children = statusBarWidgets
  }

  private val centerPane = new StackPane {
    id = "center-pane"
    children = new BorderPane { center <== currentView.delegate.map(_.centerPane.delegate) }
  }

  root = new BorderPane {
    id = "main-root-pane"
    top = new VBox(topBar, controlBar)
    center = centerPane
    bottom = statusBarPane
  }
}

object ApplicationScene {

  case class Balances(
    bitcoin: ReadOnlyObjectProperty[Option[BitcoinBalance]],
    fiat: ReadOnlyObjectProperty[Option[FiatBalance[Euro.type]]]
  )
}
