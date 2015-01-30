package coinffeine.gui.application.properties

import scalafx.beans.property._

import coinffeine.model.currency._
import coinffeine.model.market.AnyOrderPrice

trait OperationProperties {
  val sourceProperty: ReadOnlyObjectProperty[AnyRef]
  val idProperty: ReadOnlyStringProperty
  val operationTypeProperty: ReadOnlyStringProperty
  val statusProperty: ReadOnlyStringProperty
  val amountProperty: ReadOnlyObjectProperty[Bitcoin.Amount]
  val priceProperty: ReadOnlyObjectProperty[AnyOrderPrice]
  val progressProperty: ReadOnlyDoubleProperty
  val isCancellable: ReadOnlyBooleanProperty
}
