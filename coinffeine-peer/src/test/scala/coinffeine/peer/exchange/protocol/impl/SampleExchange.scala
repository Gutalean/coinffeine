package coinffeine.peer.exchange.protocol.impl

import coinffeine.model.bitcoin.{KeyPair, NetworkComponent}
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange._
import coinffeine.model.network.PeerId
import coinffeine.model.payment.PaymentProcessor
import coinffeine.peer.exchange.protocol._

trait SampleExchange { this: NetworkComponent =>

  val participants = Both(
    buyer = Exchange.PeerInfo("buyerAccount", new KeyPair()),
    seller = Exchange.PeerInfo("sellerAccount", new KeyPair())
  )

  val requiredSignatures = participants.map(_.bitcoinKey).toSeq

  val peerIds = Both(buyer = PeerId("buyer"), seller = PeerId("seller"))

  val amounts = Exchange.Amounts(
    bitcoinAmount = 1.BTC,
    fiatAmount = 1000.EUR,
    breakdown = Exchange.StepBreakdown(SampleExchange.IntermediateSteps)
  )

  val parameters = Exchange.Parameters(lockTime = 10, network)

  val buyerBlockedFunds = Exchange.BlockedFunds(fiat = Some(PaymentProcessor.BlockedFundsId(1)))
  val sellerBlockedFunds = Exchange.BlockedFunds(fiat = None)

  val buyerExchange = NonStartedExchange(
    id = ExchangeId("id"),
    role = BuyerRole,
    counterpartId = peerIds.seller,
    amounts,
    buyerBlockedFunds,
    parameters,
    brokerId = PeerId("broker")
  )
  val sellerExchange = buyerExchange.copy(
    role = SellerRole,
    counterpartId = peerIds.buyer,
    blockedFunds = sellerBlockedFunds
  )

  val buyerHandshakingExchange =
    HandshakingExchange(participants.buyer, participants.seller, buyerExchange)

  val sellerHandshakingExchange =
    HandshakingExchange(participants.seller, participants.buyer, sellerExchange)
}

object SampleExchange {
  val IntermediateSteps = 10
}
