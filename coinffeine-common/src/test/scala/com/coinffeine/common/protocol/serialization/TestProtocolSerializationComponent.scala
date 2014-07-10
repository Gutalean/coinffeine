package com.coinffeine.common.protocol.serialization

import scala.util.Random

import com.coinffeine.common.Currency.Bitcoin
import com.coinffeine.common.Currency.Implicits._
import com.coinffeine.common.exchange.{Both, Exchange, PeerId}
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
    exchangeId = Exchange.Id.random(),
    amount = randomSatoshi() BTC,
    price = randomEuros() EUR,
    peers = Both(
      buyer = PeerId("bob"),
      seller = PeerId("sam")
    )
  )

  private def randomSatoshi() =
    Math.round(Random.nextDouble() * Bitcoin.OneBtcInSatoshi.doubleValue()) /
      Bitcoin.OneBtcInSatoshi.doubleValue()

  private def randomEuros() =
    Math.round(Random.nextDouble() * 100) / 100
}
