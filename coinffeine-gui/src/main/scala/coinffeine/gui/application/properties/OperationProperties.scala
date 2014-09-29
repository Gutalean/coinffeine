package coinffeine.gui.application.properties

import scalafx.beans.property._

import coinffeine.model.currency._
import coinffeine.model.market.Price

trait OperationProperties {
  val idProperty: ReadOnlyStringProperty
  val operationTypeProperty: ReadOnlyStringProperty
  val statusProperty: ReadOnlyStringProperty
  val amountProperty: ReadOnlyObjectProperty[Bitcoin.Amount]
  val priceProperty: ReadOnlyObjectProperty[Price[_ <: FiatCurrency]]
  val progressProperty: ReadOnlyDoubleProperty
  val isCancellable: ReadOnlyBooleanProperty
}
