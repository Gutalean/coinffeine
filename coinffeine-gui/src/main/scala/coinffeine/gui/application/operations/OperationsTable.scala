package coinffeine.gui.application.operations

import javafx.beans.value.ObservableValue
import javafx.scene.control.TreeTableColumn.CellDataFeatures
import javafx.scene.control.{TreeTableColumn, TreeTableView}
import javafx.util.Callback
import scalafx.geometry.Side
import scalafx.scene.control.{Label, TreeItem}

import org.controlsfx.control.MasterDetailPane

import coinffeine.gui.application.properties.{OperationProperties, PeerOrders}
import coinffeine.gui.util.ScalafxImplicits._
import coinffeine.model.currency._
import coinffeine.model.market.AnyPrice

class OperationsTable(peerOrders: PeerOrders) extends MasterDetailPane {

  private val table = {
    val root = new TreeItem[OperationProperties]()
    root.setExpanded(true)

    peerOrders.bindToList(root.children) { order =>
      val item = new TreeItem[OperationProperties](order)
      item
    }

    val table = new TreeTableView[OperationProperties](root)
    table.setShowRoot(false)

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
      new TreeTableColumn[OperationProperties, AnyPrice] {
        setText("Price")
        setCellValueFactory(listen[OperationProperties, AnyPrice] { props =>
          props.priceProperty
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

  val selected = table.getSelectionModel.selectedItemProperty()

  private def configMasterDetail(): Unit = {
    setMasterNode(table)
    setDetailNode(new Label("Here goes the details for the selected order"))
    setDetailSide(Side.BOTTOM)
  }

  private def listen[A, B](f: A => ObservableValue[B]) = {
    new Callback[CellDataFeatures[A, B], ObservableValue[B]] {
      override def call(param: CellDataFeatures[A, B]) = f(param.getValue.getValue)
    }
  }

  configMasterDetail()
}
