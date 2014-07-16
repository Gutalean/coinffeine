package coinffeine.peer.exchange.protocol.impl

import coinffeine.model.bitcoin.{KeyPair, NetworkComponent}
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange._
import coinffeine.model.network.PeerId
import coinffeine.peer.exchange.protocol._

trait SampleExchange { this: NetworkComponent =>

  val participants = Both(
    buyer = Exchange.PeerInfo("buyerAccount", new KeyPair()),
    seller = Exchange.PeerInfo("sellerAccount", new KeyPair())
  )

  val exchange = NonStartedExchange(
    id = ExchangeId("id"),
    amounts = Exchange.Amounts(
      bitcoinAmount = 1.BTC,
      fiatAmount = 1000.EUR,
      breakdown = Exchange.StepBreakdown(SampleExchange.IntermediateSteps)
    ),
    parameters = Exchange.Parameters(lockTime = 10, network),
    peerIds = Both(buyer = PeerId("buyer"), seller = PeerId("seller")),
    brokerId = PeerId("broker")
  )

  val buyerExchange =
    HandshakingExchange(BuyerRole, participants.buyer, participants.seller, exchange)

  val sellerExchange =
    HandshakingExchange(SellerRole, participants.seller, participants.buyer, exchange)
}

object SampleExchange {
  val IntermediateSteps = 10
}
