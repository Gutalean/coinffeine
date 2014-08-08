package coinffeine.peer.market

import scala.concurrent.duration._

import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.bitcoin.BlockedCoinsId
import coinffeine.model.currency.Implicits._
import coinffeine.model.currency.{BitcoinAmount, FiatAmount}
import coinffeine.model.payment.PaymentProcessor.BlockedFundsId
import coinffeine.peer.bitcoin.WalletActor
import coinffeine.peer.bitcoin.WalletActor.BlockedBitcoins
import coinffeine.peer.market.OrderFundsActor._
import coinffeine.peer.payment.PaymentProcessorActor

class OrderFundsActorTest extends AkkaSpec {

  "The order funds actor" should "notify when funds become available at first" in new Fixture {
    givenRequestedFunds(10.EUR, 0.1.BTC)
    givenFiatBecomeAvailable()
    expectFundsToBecomeAvailable()
  }

  it should "notify if funds become unavailable or available again" in new Fixture {
    givenRequestedFunds(10.EUR, 0.1.BTC)
    givenFiatBecomeUnavailable()
    expectFundsToBecomeUnavailable()
    givenFiatBecomeAvailable()
    expectFundsToBecomeAvailable()
  }

  it should "keep requesting bitcoin funds until succeeding" in new Fixture {
    actor ! BlockFunds(100.EUR, 1.BTC, walletProbe.ref, paymentProcessor.ref)
    givenFiatBlockingOf(100.EUR)
    givenFiatBecomeAvailable()

    walletProbe.expectMsg(WalletActor.BlockBitcoins(1.BTC))
    walletProbe.reply(WalletActor.CannotBlockBitcoins)
    walletProbe.expectMsg(WalletActor.SubscribeToWalletChanges)
    walletProbe.expectNoMsg(100.millis)

    walletProbe.send(actor, WalletActor.WalletChanged)
    givenBtcBlockingOf(1.BTC)

    expectFundsToBecomeAvailable()
  }

  it should "release blocked funds" in new Fixture {
    givenRequestedFunds(10.EUR, 0.1.BTC)
    givenFiatBecomeAvailable()
    expectFundsToBecomeAvailable()
    actor ! UnblockFunds
    expectBtcUnblocking()
    expectFiatUnblocking()
  }

  it should "be able to block only bitcoins" in new Fixture {
    givenRequestedFunds(0.EUR, 0.1.BTC)
    expectFundsToBecomeAvailable()
    actor ! UnblockFunds
    expectBtcUnblocking()
    expectNoFiatUnblocking()
  }

  trait Fixture {
    val walletProbe = TestProbe()
    val paymentProcessor = TestProbe()
    val actor = system.actorOf(OrderFundsActor.props)
    val fiatFunds = BlockedFundsId(1)
    val btcFunds = BlockedCoinsId(1)

    def givenFiatBlockingOf(amount: FiatAmount): Unit = {
      paymentProcessor.expectMsg(PaymentProcessorActor.BlockFunds(amount, actor))
      paymentProcessor.reply(fiatFunds)
    }

    def givenNoFiatBlocking(): Unit = {
      paymentProcessor.expectNoMsg(100.millis)
    }

    def givenFiatBecomeAvailable(): Unit = {
      paymentProcessor.reply(PaymentProcessorActor.AvailableFunds(fiatFunds))
    }

    def givenFiatBecomeUnavailable(): Unit = {
      paymentProcessor.reply(PaymentProcessorActor.UnavailableFunds(fiatFunds))
    }

    def givenBtcBlockingOf(amount: BitcoinAmount): Unit = {
      walletProbe.expectMsg(WalletActor.BlockBitcoins(amount))
      walletProbe.reply(BlockedBitcoins(btcFunds))
    }

    def givenRequestedFunds(fiatAmount: FiatAmount, bitcoinAmount: BitcoinAmount): Unit = {
      actor ! BlockFunds(fiatAmount, bitcoinAmount, walletProbe.ref, paymentProcessor.ref)
      if (fiatAmount.isPositive) {
        givenFiatBlockingOf(fiatAmount)
      } else {
        givenNoFiatBlocking()
      }
      givenBtcBlockingOf(bitcoinAmount)
    }

    def expectFundsToBecomeAvailable(): Unit = {
      fishForMessage() {
        case AvailableFunds(_) => true
        case UnavailableFunds => false
      }
    }

    def expectFundsToBecomeUnavailable(): Unit = {
      fishForMessage() {
        case AvailableFunds(_) => false
        case UnavailableFunds => true
      }
    }

    def expectBtcUnblocking(): Unit = {
      walletProbe.expectMsg(WalletActor.UnblockBitcoins(btcFunds))
    }

    def expectFiatUnblocking(): Unit = {
      paymentProcessor.expectMsg(PaymentProcessorActor.UnblockFunds(fiatFunds))
    }

    def expectNoFiatUnblocking(): Unit = {
      paymentProcessor.expectNoMsg(100.millis)
    }
  }
}
