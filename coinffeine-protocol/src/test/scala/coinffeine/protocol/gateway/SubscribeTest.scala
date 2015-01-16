package coinffeine.protocol.gateway

import coinffeine.common.test.UnitTest
import coinffeine.model.currency.Euro
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.market.Market
import coinffeine.model.network.{BrokerId, PeerId}
import coinffeine.protocol.gateway.MessageGateway.{ReceiveMessage, Subscribe}
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.messages.brokerage.QuoteRequest
import coinffeine.protocol.messages.exchange.MicropaymentChannelClosed

class SubscribeTest extends UnitTest {

  val quotesFromBroker = Subscribe.fromBroker { case QuoteRequest(_) => }
  val relevantMessage = QuoteRequest(Market(Euro))
  val irrelevantMessage = MicropaymentChannelClosed(ExchangeId.random())
  def isAccepted(receive: ReceiveMessage[_ <: PublicMessage]) =
    quotesFromBroker.filter.isDefinedAt(receive)

  "Subscribing filters from broker" should "discard messages from peers" in {
    isAccepted(ReceiveMessage(irrelevantMessage, PeerId.random())) shouldBe false
    isAccepted(ReceiveMessage(relevantMessage, PeerId.random())) shouldBe false
  }

  it should "accept relevant messages from the broker" in {
    isAccepted(ReceiveMessage(irrelevantMessage, BrokerId)) shouldBe false
    isAccepted(ReceiveMessage(relevantMessage, BrokerId)) shouldBe true
  }
}
