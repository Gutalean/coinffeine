package com.coinffeine.client.api

import java.io.Closeable

import com.coinffeine.common.Order
import com.coinffeine.common.paymentprocessor.PaymentProcessor
import com.coinffeine.common.protocol.ProtocolConstants

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
  case class OrderSubmittedEvent(order: Order) extends Event
}

