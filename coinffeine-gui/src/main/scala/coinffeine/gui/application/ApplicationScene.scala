package coinffeine.gui.application

import java.text.DecimalFormat

import coinffeine.gui.application.help.AboutDialog
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.preferences.PreferencesForm
import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.{NodeStyles, PaneStyles, Stylesheets, TextStyles}
import coinffeine.model.currency._
import coinffeine.peer.config.SettingsProvider

import scalafx.Includes._
import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}
import scalafx.event.ActionEvent
import scalafx.scene.Node
import scalafx.scene.control._
import scalafx.scene.layout._

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

  val menuBar = new MenuBar {
    useSystemMenuBar = true

    menus = Seq(
      new Menu("Edit") {
        items = Seq(
          new MenuItem("Preferences") {
            onAction = { e: ActionEvent =>
              val form = new PreferencesForm(settingsProvider)
              form.show()
            }
          }
        )
      },
      new Menu("Help") {
        items = Seq(
          new MenuItem("About...") {
            onAction = { e: ActionEvent =>
              val dialog = new AboutDialog
              dialog.show()
            }
          }
        )
      }
    )
  }

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

  val topbar = new VBox {
    id = "top-bar"
  }

  val balancePane = new VBox {
    id = "balance-pane"
    content = Seq(
      new HBox with PaneStyles.MinorSpacing {
        content = Seq(
          new Label("AVAILABLE") with TextStyles.NeutralNews,
          new Label("BALANCE") with TextStyles.NeutralNews with TextStyles.Boldface)
      },
      new Label with TextStyles.GoodNews with TextStyles.SuperBoldface with TextStyles.Huge {
        text <== balances.fiat.delegate.mapToString {
          case Some(b) => s"€${ApplicationScene.BalanceFormat.format(b.amount)}"
          case None => "€?"
        }
      },
      new HBox {
        content = Seq(
          new Label with TextStyles.GoodNews with TextStyles.Big {
            text <== balances.bitcoin.delegate.mapToString {
              case Some(b) => ApplicationScene.BalanceFormat.format(b.available.value)
              case None => "?"
            }
          },
          new Label("BTC") with TextStyles.GoodNews with TextStyles.Boldface with TextStyles.Big)
      }
    )
  }

  val viewSelectorPane = new HBox with NodeStyles.HExpand {
    id = "view-selector-pane"
    content = viewSelector
  }

  val controlPane = new Pane {
    minWidth = 300
  }

  val controlBar = new HBox {
    id = "control-bar"
    content = Seq(balancePane, viewSelectorPane, controlPane)
  }

  val currentView = new ObjectProperty[ApplicationView](this, "currentView", views.head)

  private val statusBarPane = new HBox with PaneStyles.StatusBar {
    id = "status"
    prefHeight = 25
    content = statusBarWidgets
  }

  private val centerPane = new StackPane {
    id = "center-pane"
    content = new BorderPane { center <== currentView.delegate.map(_.centerPane.delegate) }
  }

  root = {
    val mainPane = new VBox() {
      content = Seq(
        menuBar,
        topbar,
        controlBar,
        new BorderPane {
          id = "main-root-pane"
          vgrow = Priority.Always
          bottom = statusBarPane
          center = centerPane
        }
      )
    }
    mainPane
  }
}

object ApplicationScene {

  case class Balances(
    bitcoin: ReadOnlyObjectProperty[Option[BitcoinBalance]],
    fiat: ReadOnlyObjectProperty[Option[FiatBalance[Euro.type]]]
  )

  private val BalanceFormat = new DecimalFormat("###,###,##0.00######")
}
