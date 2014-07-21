package coinffeine.protocol.serialization

import scala.util.Random

import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.market.OrderId
import coinffeine.model.network.PeerId
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.messages.brokerage.OrderMatch
import coinffeine.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage

/** Provides a serialization that behaves like the default one but allowing injection of
  * serialization errors and other testing goodies.
  */
trait TestProtocolSerializationComponent extends ProtocolSerializationComponent {

  override lazy val protocolSerialization = new TestProtocolSerialization

  def randomMessageAndSerialization(senderId: PeerId): (PublicMessage, CoinffeineMessage) = {
    val message = randomOrderMatch()
    (message, protocolSerialization.toProtobuf(message, senderId))
  }

  def randomOrderMatch(): OrderMatch = OrderMatch(
    orderId = OrderId.random(),
    exchangeId = ExchangeId.random(),
    amount = randomSatoshi() BTC,
    price = randomEuros() EUR,
    lockTime = 42L,
    counterpart = PeerId("bob")
  )

  private def randomSatoshi() =
    Math.round(Random.nextDouble() * Bitcoin.OneBtcInSatoshi.doubleValue()) /
      Bitcoin.OneBtcInSatoshi.doubleValue()

  private def randomEuros() =
    Math.round(Random.nextDouble() * 100) / 100
}
