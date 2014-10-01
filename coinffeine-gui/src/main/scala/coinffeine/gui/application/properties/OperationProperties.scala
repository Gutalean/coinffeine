package coinffeine.gui.application.properties

import scalafx.beans.property._

import coinffeine.model.currency._
import coinffeine.model.market.AnyPrice

trait OperationProperties {
  val sourceProperty: ReadOnlyObjectProperty[AnyRef]
  val idProperty: ReadOnlyStringProperty
  val operationTypeProperty: ReadOnlyStringProperty
  val statusProperty: ReadOnlyStringProperty
  val amountProperty: ReadOnlyObjectProperty[Bitcoin.Amount]
  val priceProperty: ReadOnlyObjectProperty[AnyPrice]
  val progressProperty: ReadOnlyDoubleProperty
  val isCancellable: ReadOnlyBooleanProperty
}
