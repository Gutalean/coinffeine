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
  def paymentProcessor: CoinffeinePaymentProcessor
  def marketStats: MarketStats
  def protocolConstants: ProtocolConstants

  def observe(handler: EventHandler): Unit
}
