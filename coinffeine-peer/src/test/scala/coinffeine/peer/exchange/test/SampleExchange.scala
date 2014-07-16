package coinffeine.peer.exchange.test

import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.bitcoin.{KeyPair, PublicKey}
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange._
import coinffeine.model.network.PeerId
import com.coinffeine.common.exchange._

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

  val exchange = NonStartedExchange(
    id = Exchange.Id("id"),
    amounts = Exchange.Amounts(
      bitcoinAmount = 10.BTC,
      fiatAmount = 10.EUR,
      breakdown = Exchange.StepBreakdown(intermediateSteps = 10)
    ),
    parameters = Exchange.Parameters(lockTime = 25, network),
    peerIds = Both(buyer = PeerId("buyer"), seller = PeerId("seller")),
    brokerId
  )

  val buyerRunningExchange = RunningExchange(
    MockExchangeProtocol.DummyDeposits,
    HandshakingExchange(BuyerRole, participants.buyer, participants.seller, exchange)
  )

  val sellerRunningExchange = RunningExchange(
    MockExchangeProtocol.DummyDeposits,
    HandshakingExchange(SellerRole, participants.seller, participants.buyer, exchange)
  )
}
