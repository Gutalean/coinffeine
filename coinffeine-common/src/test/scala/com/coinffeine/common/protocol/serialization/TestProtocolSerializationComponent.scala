package com.coinffeine.common.protocol.serialization

import coinffeine.model.currency.Currency
import coinffeine.model.exchange.Exchange
import coinffeine.model.market.OrderId
import coinffeine.model.network.PeerId

import scala.util.Random
import Currency.Bitcoin
import coinffeine.model.currency.Implicits._
import com.coinffeine.common.protocol.ProtocolConstants
import com.coinffeine.common.protocol.messages.PublicMessage
import com.coinffeine.common.protocol.messages.brokerage.OrderMatch
import com.coinffeine.common.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage

/** Provides a serialization that behaves like the default one but allowing injection of
  * serialization errors and other testing goodies.
  */
trait TestProtocolSerializationComponent extends ProtocolSerializationComponent {
  this: ProtocolConstants.Component =>

  override lazy val protocolSerialization = new TestProtocolSerialization(protocolConstants.version)

  def randomMessageAndSerialization(senderId: PeerId): (PublicMessage, CoinffeineMessage) = {
    val message = randomOrderMatch()
    (message, protocolSerialization.toProtobuf(message, senderId))
  }

  def randomOrderMatch(): OrderMatch = OrderMatch(
    orderId = OrderId.random(),
    exchangeId = Exchange.Id.random(),
    amount = randomSatoshi() BTC,
    price = randomEuros() EUR,
    counterpart = PeerId("bob")
  )

  private def randomSatoshi() =
    Math.round(Random.nextDouble() * Bitcoin.OneBtcInSatoshi.doubleValue()) /
      Bitcoin.OneBtcInSatoshi.doubleValue()

  private def randomEuros() =
    Math.round(Random.nextDouble() * 100) / 100
}
