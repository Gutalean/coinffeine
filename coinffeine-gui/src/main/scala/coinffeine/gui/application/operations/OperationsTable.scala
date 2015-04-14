package coinffeine.gui.application.operations

import javafx.beans.property.ReadOnlyProperty
import javafx.beans.value.ObservableValue
import javafx.collections.ObservableList
import javafx.scene.Node
import javafx.scene.control.TreeTableColumn.CellDataFeatures
import javafx.scene.control.{TreeTableColumn, TreeTableView}
import javafx.util.Callback
import scalafx.Includes._
import scalafx.beans.property.BooleanProperty
import scalafx.geometry.{HPos, Side}
import scalafx.scene.control.{Label, TreeItem}
import scalafx.scene.image.ImageView
import scalafx.scene.layout.{ColumnConstraints, GridPane, Priority, StackPane}

import org.controlsfx.control.MasterDetailPane

import coinffeine.gui.application.properties.{OperationProperties, PeerOrders}
import coinffeine.gui.beans.Implicits._
import coinffeine.model.currency._
import coinffeine.model.exchange._
import coinffeine.model.market._

class OperationsTable(peerOrders: PeerOrders) extends MasterDetailPane {

  setId("operations-master-detail-pane")

  private val table = {
    val root = new TreeItem[OperationProperties]()
    root.setExpanded(true)

    peerOrders.bindToList(root.children) { order =>
      val item = new TreeItem[OperationProperties](order)

      item.graphic = order.orderTypeProperty.value match {
        case Bid => new ImageView { styleClass += "operations-bid-icon" }
        case Ask => new ImageView { styleClass += "operations-ask-icon" }
      }

      order.exchanges.bindToList(item.children) { exchange =>
        new TreeItem[OperationProperties](exchange)
      }

      item
    }

    val table = new TreeTableView[OperationProperties](root)
    table.setId("operations-table")
    table.setShowRoot(false)
    table.setPlaceholder(new Label("No active operations found"))

    table.getColumns.addAll(
      new TreeTableColumn[OperationProperties, String] {
        setText("ID")
        setCellValueFactory(listen[OperationProperties, String] { props =>
          props.idProperty
        })
      },
      new TreeTableColumn[OperationProperties, String] {
        setText("Status")
        setCellValueFactory(listen[OperationProperties, String] { props =>
          props.statusProperty
        })
      },
      new TreeTableColumn[OperationProperties, String] {
        setText("Type")
        setCellValueFactory(listen[OperationProperties, String] { props =>
          props.operationTypeProperty
        })
      },
      new TreeTableColumn[OperationProperties, Bitcoin.Amount] {
        setText("Amount")
        setCellValueFactory(listen[OperationProperties, Bitcoin.Amount] { props =>
          props.amountProperty
        })
      },
      new TreeTableColumn[OperationProperties, String] {
        setText("Price")
        setCellValueFactory(listen[OperationProperties, String] { props =>
          props.priceProperty.delegate.map {
            case MarketPrice(_) => "Market price"
            case LimitPrice(limit) => limit.toString
          }
        })
      },
      new TreeTableColumn[OperationProperties, String] {
        setText("Progress")
        setCellValueFactory(listen[OperationProperties, String] { props =>
          props.progressProperty.delegate.map { p =>
          val percentage = (p.doubleValue() * 100.0).toInt
          s"$percentage%"
        }})
      }
    )
    table
  }

  val selected: ReadOnlyProperty[Option[OperationProperties]] =
    table.getSelectionModel.selectedItemProperty()
      .map(item => Option(item).map(_.getValue))
      .toReadOnlyProperty
  val showDetails: BooleanProperty = BooleanProperty(value = false)

  private def configMasterDetail(): Unit = {
    this.vgrow = Priority.Always
    setMasterNode(table)
    setDetailNode(new StackPane() {
      maxWidth = Double.MaxValue
      bindToSelected(content)
    }.delegate)
    showDetailNodeProperty().bind(showDetails)
    setDetailSide(Side.BOTTOM)
  }

  private def bindToSelected(content: ObservableList[Node]): Unit = {
    selected.onChange { (_, _, newValue) =>
      newValue.foreach { item =>
        item.sourceProperty.delegate.bindToList(content) {
          case order: AnyCurrencyOrder =>
            orderDetailsContent(order)
          case exchange: AnyExchange =>
            exchangeDetailsContent(exchange)
        }
      }
    }
  }

  private def orderDetailsContent(order: AnyCurrencyOrder): Seq[Node] = Seq(new GridPane() {
    id = "operations-details-pane"
    columnConstraints = fieldColumnConstraints(2)
    add(fieldName("Order ID"), 0, 0)
    add(fieldValue(order.id.value), 1, 0)
    add(fieldName("Type"), 2, 0)
    add(fieldValue(order.orderType.toString), 3, 0)
    add(fieldName("Status"), 0, 1)
    add(fieldValue(order.status.toString.capitalize), 1, 1)
    add(fieldName("Amount"), 0, 2)
    add(fieldValue(order.amount.toString), 1, 2)
    add(fieldName("Price"), 2, 2)
    add(fieldValue(order.price.toString), 3, 2)
  }.delegate)

  private def exchangeDetailsContent(exchange: AnyExchange): Seq[Node] =
    Seq(new GridPane() {
      id = "operations-details-pane"
      columnConstraints = fieldColumnConstraints(3)
      add(fieldName("Exchange ID"), 0, 0)
      add(fieldValue(exchange.id.value), 1, 0, 3, 1)
      add(fieldName("Role"), 4, 0)
      add(fieldValue(exchange.role.toString.capitalize), 5, 0)

      add(fieldName("Status"), 0, 1)
      add(fieldValue(exchange.status.name.capitalize), 1, 1)
      add(fieldName("Counterpart"), 2, 1)
      add(fieldValue(exchange.counterpartId.value), 3, 1)
      add(fieldName("Locktime"), 4, 1)
      add(fieldValue(exchange.parameters.lockTime.toString), 5, 1)

      add(fieldName("Gross bitcoin"), 0, 2)
      add(fieldValue(exchange.amounts.grossBitcoinExchanged.toString), 1, 2)
      add(fieldName("Net bitcoin"), 2, 2)
      add(fieldValue(exchange.amounts.netBitcoinExchanged.toString), 3, 2)

      add(fieldName("Gross fiat"), 0, 3)
      add(fieldValue(exchange.amounts.grossFiatExchanged.toString), 1, 3)
      add(fieldName("Net fiat"), 2, 3)
      add(fieldValue(exchange.amounts.netFiatExchanged.toString), 3, 3)

      add(fieldName("Seller deposit input"), 0, 4)
      add(fieldValue(exchange.amounts.deposits.seller.input.toString), 1, 4)
      add(fieldName("Seller deposit output"), 2, 4)
      add(fieldValue(exchange.amounts.deposits.seller.output.toString), 3, 4)
      add(fieldName("Seller deposit fee"), 4, 4)
      add(fieldValue(exchange.amounts.deposits.seller.fee.toString), 5, 4)

      add(fieldName("Buyer deposit input"), 0, 5)
      add(fieldValue(exchange.amounts.deposits.buyer.input.toString), 1, 5)
      add(fieldName("Buyer deposit output"), 2, 5)
      add(fieldValue(exchange.amounts.deposits.buyer.output.toString), 3, 5)
      add(fieldName("Buyer deposit fee"), 4, 5)
      add(fieldValue(exchange.amounts.deposits.buyer.fee.toString), 5, 5)
    }.delegate)

  private def listen[A, B](f: A => ObservableValue[B]) = {
    new Callback[CellDataFeatures[A, B], ObservableValue[B]] {
      override def call(param: CellDataFeatures[A, B]) = f(param.getValue.getValue)
    }
  }

  private def fieldName(name: String) = new Label(s"$name:") {
    styleClass = Seq("field-name")
  }

  private def fieldValue(value: String) = new Label(value) {
    styleClass = Seq("field-value")
  }

  private val fieldNameColumnConstraints = new ColumnConstraints() {
    halignment = HPos.Right
    hgrow = Priority.Always
  }

  private val fieldValueColumnConstraints = new ColumnConstraints() {
    halignment = HPos.Left
    hgrow = Priority.Always
  }

  private def fieldColumnConstraints(fieldCols: Int) =
    Seq.fill(fieldCols)(Seq(fieldNameColumnConstraints, fieldValueColumnConstraints)).flatten.toSeq

  configMasterDetail()
}
