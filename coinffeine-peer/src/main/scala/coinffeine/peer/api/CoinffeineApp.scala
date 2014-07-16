package coinffeine.peer.api

import java.io.Closeable

import coinffeine.model.currency.FiatAmount
import coinffeine.model.market.{OrderBookEntry, OrderId}
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.payment.PaymentProcessor

/** Coinffeine application interface */
trait CoinffeineApp extends Closeable {

  def network: CoinffeineNetwork
  def wallet: CoinffeineWallet
  def paymentProcessors: Set[PaymentProcessor.Component]
  def marketStats: MarketStats
  def protocolConstants: ProtocolConstants

  def observe(handler: EventHandler): Unit
}

object CoinffeineApp {

  /** A marking trait used to define Coinffeine events. */
  sealed trait Event

  /** An event triggered each time a new order is submitted. */
  case class OrderSubmittedEvent(order: OrderBookEntry[FiatAmount]) extends Event

  /** An event triggered each time an order is cancelled. */
  case class OrderCancelledEvent(orderId: OrderId) extends Event
}

