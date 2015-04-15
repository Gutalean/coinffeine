package coinffeine.protocol.serialization.test

import scala.util.Random

import coinffeine.model.Both
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.currency.{Bitcoin, CurrencyAmount, Euro}
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.market.OrderId
import coinffeine.model.network.PeerId
import coinffeine.protocol.messages.brokerage.OrderMatch
import coinffeine.protocol.serialization._
import coinffeine.protocol.serialization.protobuf.ProtobufProtocolSerialization

/** Provides a serialization that behaves like the default one but allowing injection of
  * serialization errors and other testing goodies.
  */
trait TestProtocolSerializationComponent extends ProtocolSerializationComponent {

  override lazy val protocolSerialization =
    new ProtobufProtocolSerialization(new TransactionSerialization(CoinffeineUnitTestNetwork))

  def randomOrderMatch() = OrderMatch(
    orderId = OrderId.random(),
    exchangeId = ExchangeId.random(),
    bitcoinAmount = Both.fill(randomSatoshi()),
    fiatAmount = Both.fill(randomEuros()),
    lockTime = 42L,
    counterpart = PeerId.hashOf("bob")
  )

  private def randomSatoshi() = CurrencyAmount.closestAmount(Random.nextDouble(), Bitcoin)
  private def randomEuros() = CurrencyAmount.closestAmount(Random.nextDouble(), Euro)
}
