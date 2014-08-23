package coinffeine.model.exchange

import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.bitcoin.{BlockedCoinsId, KeyPair, PublicKey}
import coinffeine.model.currency.Implicits._
import coinffeine.model.network.PeerId
import coinffeine.model.payment.PaymentProcessor

trait SampleExchange extends CoinffeineUnitTestNetwork.Component {

  val participants = Both(
    buyer = Exchange.PeerInfo(
      paymentProcessorAccount = "buyer",
      bitcoinKey = new PublicKey()
    ),
    seller = Exchange.PeerInfo(
      paymentProcessorAccount = "seller",
      bitcoinKey = new KeyPair()
    )
  )

  val requiredSignatures = participants.map(_.bitcoinKey).toSeq

  val peerIds = Both(buyer = PeerId("buyer"), seller = PeerId("seller"))

  val amounts = Exchange.Amounts(
    deposits = Both(buyer = 2.BTC, seller = 11.BTC),
    refunds = Both(buyer = 1.BTC, seller = 10.BTC),
    bitcoinExchanged = 10.BTC,
    fiatExchanged = 10.EUR,
    breakdown = Exchange.StepBreakdown(intermediateSteps = 10)
  )

  val exchangeId = ExchangeId("id")

  val parameters = Exchange.Parameters(lockTime = 25, network)

  val buyerBlockedFunds = Exchange.BlockedFunds(
    fiat = Some(PaymentProcessor.BlockedFundsId(1)),
    bitcoin = BlockedCoinsId(1)
  )
  val sellerBlockedFunds = Exchange.BlockedFunds(fiat = None, bitcoin = BlockedCoinsId(2))

  val buyerExchange = Exchange.notStarted(exchangeId, BuyerRole, peerIds.seller, amounts,
    parameters, buyerBlockedFunds)
  val buyerHandshakingExchange =
    buyerExchange.startHandshaking(user = participants.buyer, counterpart = participants.seller)

  val sellerExchange = Exchange.notStarted(exchangeId, SellerRole, peerIds.seller, amounts,
    parameters, buyerBlockedFunds)
  val sellerHandshakingExchange =
    sellerExchange.startHandshaking(user = participants.seller, counterpart = participants.buyer)
}

object SampleExchange {
  val IntermediateSteps = 10
}
