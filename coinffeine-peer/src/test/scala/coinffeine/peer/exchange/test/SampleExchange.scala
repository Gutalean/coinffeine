package coinffeine.peer.exchange.test

import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.bitcoin.{BlockedCoinsId, KeyPair, PublicKey}
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange._
import coinffeine.model.network.PeerId
import coinffeine.model.payment.PaymentProcessor
import coinffeine.peer.exchange.protocol._

trait SampleExchange extends CoinffeineUnitTestNetwork.Component {

  val brokerId = PeerId("broker")

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

  val peerIds = Both(buyer = PeerId("buyer"), seller = PeerId("seller"))

  val amounts = Exchange.Amounts(
    bitcoinAmount = 10.BTC,
    fiatAmount = 10.EUR,
    breakdown = Exchange.StepBreakdown(intermediateSteps = 10)
  )

  val exchangeId = ExchangeId("id")

  val parameters = Exchange.Parameters(lockTime = 25, network)

  val buyerBlockedFunds = Exchange.BlockedFunds(
    fiat = Some(PaymentProcessor.BlockedFundsId(1)),
    bitcoin = BlockedCoinsId(1)
  )
  val sellerBlockedFunds = Exchange.BlockedFunds(fiat = None, bitcoin = BlockedCoinsId(2))

  val buyerExchange = Exchange.nonStarted(exchangeId, BuyerRole, peerIds.seller, amounts,
    parameters, brokerId, buyerBlockedFunds)
  val buyerHandshakingExchange =
    buyerExchange.startHandshaking(user = participants.buyer, counterpart = participants.seller)
  val buyerRunningExchange =
    buyerHandshakingExchange.startExchanging(deposits = MockExchangeProtocol.DummyDeposits)

  val sellerExchange = Exchange.nonStarted(exchangeId, SellerRole, peerIds.seller, amounts,
    parameters, brokerId, buyerBlockedFunds)
  val sellerHandshakingExchange =
    sellerExchange.startHandshaking(user = participants.seller, counterpart = participants.buyer)
  val sellerRunningExchange =
    sellerHandshakingExchange.startExchanging(deposits = MockExchangeProtocol.DummyDeposits)
}
