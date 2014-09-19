package coinffeine.protocol.serialization

import scala.util.Random

import coinffeine.model.currency.Currency.{Bitcoin, Euro}
import coinffeine.model.currency.CurrencyAmount
import coinffeine.model.exchange.{Both, ExchangeId}
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

  def randomMessageAndSerialization(): (PublicMessage, CoinffeineMessage) = {
    val message = randomOrderMatch()
    (message, protocolSerialization.toProtobuf(message))
  }

  def randomOrderMatch() = OrderMatch(
    orderId = OrderId.random(),
    exchangeId = ExchangeId.random(),
    bitcoinAmount = Both.fill(randomSatoshi()),
    fiatAmount = Both.fill(randomEuros()),
    lockTime = 42L,
    counterpart = PeerId("bob")
  )

  private def randomSatoshi() = CurrencyAmount.closestAmount(Random.nextDouble(), Bitcoin)
  private def randomEuros() = CurrencyAmount.closestAmount(Random.nextDouble(), Euro)
}
