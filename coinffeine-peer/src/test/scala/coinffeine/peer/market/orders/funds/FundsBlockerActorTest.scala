package coinffeine.peer.market.orders.funds

import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.testkit.TestProbe
import org.scalatest.Inside

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.bitcoin.BlockedCoinsId
import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange.Exchange
import coinffeine.model.market.RequiredFunds
import coinffeine.model.payment.PaymentProcessor.BlockedFundsId
import coinffeine.peer.bitcoin.WalletActor
import coinffeine.peer.payment.PaymentProcessorActor

class FundsBlockerActorTest extends AkkaSpec with Inside {

  "The order funds actor" should "notify when funds become blocked" in new Fixture {
    expectFiatFundsAreRequested()
    expectBitcoinFundsAreRequested()
    givenBitcoinFundsAreBlocked()
    givenFiatFundsAreCreated()
    givenFiatFundsBecomeAvailable()
    expectSuccessfulBlocking()
  }

  it should "not request fiat when not needed" in new Fixture(requiredFiat = 0.EUR) {
    expectBitcoinFundsAreRequested()
    expectNoRequestToPaymentProcessor()
    givenBitcoinFundsAreBlocked()
    expectSuccessfulBlocking()
  }

  it should "fail if fiat funds are not available at the end" in new Fixture {
    expectFundsAreRequested()
    givenFiatFundsAreCreated()
    givenFiatFundsBecomeUnavailable()
    givenBitcoinFundsAreBlocked()
    expectBitcoinFundsUnblocking()
    expectFiatFundsUnblocking()
    expectFailedBlocking(s"100 EUR blocked in $fiatFunds but not available")
  }

  it should "fail if bitcoin funds cannot be blocked" in new Fixture {
    expectFundsAreRequested()
    givenFiatFundsAreCreated()
    givenFiatFundsBecomeAvailable()
    givenBitcoinFundsFailToBeBlocked()
    expectFiatFundsUnblocking()
    expectFailedBlocking(s"Cannot block 1 BTC")
  }

  it should "not try to unblock fiat when it was not requested to begin with" in
    new Fixture(requiredFiat = 0.EUR) {
      expectFundsAreRequested()
      givenBitcoinFundsFailToBeBlocked()
      expectNoRequestToPaymentProcessor()
      expectFailedBlocking(s"Cannot block 1 BTC")
    }

  abstract class Fixture(requiredFiat: Euro.Amount = 100.EUR,
                         requiredBitcoin: BitcoinAmount = 1.BTC) {
    val walletProbe, paymentProcessor = TestProbe()
    val actor = system.actorOf(FundsBlockerActor.props(walletProbe.ref, paymentProcessor.ref,
      RequiredFunds(requiredBitcoin, requiredFiat), listener = self))
    val fiatFunds = BlockedFundsId(1)
    val btcFunds = BlockedCoinsId(1)

    def expectFundsAreRequested(): Unit = {
      if (requiredFiat.isPositive) expectFiatFundsAreRequested()
      else expectNoRequestToPaymentProcessor()
      expectBitcoinFundsAreRequested()
    }

    def expectBitcoinFundsAreRequested(): Unit = {
      walletProbe.expectMsg(WalletActor.BlockBitcoins(requiredBitcoin))
    }

    def expectFiatFundsAreRequested(): Unit = {
      paymentProcessor.expectMsg(PaymentProcessorActor.BlockFunds(requiredFiat))
    }

    def expectNoRequestToPaymentProcessor(): Unit = {
      paymentProcessor.expectNoMsg(100.millis)
    }

    def givenBitcoinFundsAreBlocked(): Unit = {
      walletProbe.reply(WalletActor.BlockedBitcoins(btcFunds))
    }

    def givenBitcoinFundsFailToBeBlocked(): Unit = {
      walletProbe.reply(WalletActor.CannotBlockBitcoins)
    }

    def givenFiatFundsAreCreated(): Unit = {
      paymentProcessor.reply(fiatFunds)
    }

    def givenFiatFundsBecomeAvailable(): Unit = {
      paymentProcessor.reply(PaymentProcessorActor.AvailableFunds(fiatFunds))
    }

    def givenFiatFundsBecomeUnavailable(): Unit = {
      paymentProcessor.reply(PaymentProcessorActor.UnavailableFunds(fiatFunds))
    }

    def expectBitcoinFundsUnblocking(): Unit = {
      walletProbe.expectMsg(WalletActor.UnblockBitcoins(btcFunds))
    }

    def expectFiatFundsUnblocking(): Unit = {
      paymentProcessor.expectMsg(PaymentProcessorActor.UnblockFunds(fiatFunds))
    }

    def expectNoFiatFundsUnblocking(): Unit = {
      paymentProcessor.expectNoMsg(100.millis)
    }

    def expectSuccessfulBlocking(): Unit = {
      val funds = Exchange.BlockedFunds(
        if (requiredFiat.isPositive) Some(fiatFunds) else None, btcFunds)
      expectMsg(FundsBlockerActor.BlockingResult(Success(funds)))
    }

    def expectFailedBlocking(message: String): Unit = {
      inside(expectMsgType[FundsBlockerActor.BlockingResult]) {
        case FundsBlockerActor.BlockingResult(Failure(cause)) =>
          cause.getMessage should include (message)
      }
    }
  }
}
