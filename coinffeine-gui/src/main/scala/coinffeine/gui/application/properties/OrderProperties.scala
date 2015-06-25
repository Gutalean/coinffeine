package coinffeine.gui.application.properties

import scalafx.beans.property._
import scalafx.collections.ObservableBuffer

import org.joda.time.DateTime

import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.order._

trait OrderProperties {
  def orderProperty: ReadOnlyObjectProperty[Order]
  def idProperty: ReadOnlyObjectProperty[OrderId]
  def typeProperty: ReadOnlyObjectProperty[OrderType]
  def createdOnProperty: ReadOnlyObjectProperty[DateTime]
  def amountProperty: ReadOnlyObjectProperty[BitcoinAmount]
  def priceProperty: ReadOnlyObjectProperty[OrderPrice]
  def statusProperty: ReadOnlyObjectProperty[OrderStatus]
  def progressProperty: ReadOnlyDoubleProperty
  def isCancellable: ReadOnlyBooleanProperty
  def exchanges: ObservableBuffer[ExchangeProperties]
}
