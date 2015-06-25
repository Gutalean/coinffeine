package coinffeine.gui.application.operations

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._
import coinffeine.model.exchange.Exchange.Progress
import coinffeine.model.exchange._
import coinffeine.model.network.PeerId
import coinffeine.model.order.Price
import coinffeine.model.{ActivityLog, Both}

class WeightedAveragePriceTest extends UnitTest {

  "The weighted average price" should "not be computed for zero exchanges" in {
    WeightedAveragePrice.average(Seq.empty) shouldBe None
  }

  it should "add up buyer amounts when bidding" in {
    WeightedAveragePrice.average(Seq(
      CompletedExchange(
        BuyerRole,
        Both(buyer = 1.BTC, seller = 1.003.BTC),
        Both(buyer = 100.EUR, seller = 98.EUR)
      ),
      CompletedExchange(
        BuyerRole,
        Both(buyer = 1.BTC, seller = 1.003.BTC),
        Both(buyer = 200.EUR, seller = 296.EUR)
      )
    )) shouldBe Some(Price(150.EUR))
  }

  it should "add up seller amounts when asking" in {
    WeightedAveragePrice.average(Seq(
      CompletedExchange(
        SellerRole,
        Both(buyer = 0.997.BTC, seller = 1.BTC),
        Both(buyer = 102.EUR, seller = 100.EUR)
      ),
      CompletedExchange(
        SellerRole,
        Both(buyer = 0.997.BTC, seller = 1.BTC),
        Both(buyer = 204.EUR, seller = 200.EUR)
      )
    )) shouldBe Some(Price(150.EUR))
  }

  it should "be proportional to the exchanged amount" in {
    WeightedAveragePrice.average(Seq(
      CompletedExchange(
        BuyerRole,
        Both(buyer = 1.BTC, seller = 1.003.BTC),
        Both(buyer = 100.EUR, seller = 98.EUR)
      ),
      CompletedExchange(
        BuyerRole,
        Both(buyer = 3.BTC, seller = 3.003.BTC),
        Both(buyer = 100.EUR, seller = 98.EUR)
      )
    )) shouldBe Some(Price(50.EUR))
  }

  it should "consider the proportional part of failed exchanges" in {
    WeightedAveragePrice.average(Seq(
      FailedExchange(
        BuyerRole,
        progressedBitcoin = Both(buyer = 0.5.BTC, seller = 0.503.BTC),
        exchangedBitcoin = Both(buyer = 1.BTC, seller = 1.003.BTC),
        exchangedFiat = Both(buyer = 100.EUR, seller = 98.EUR)
      ),
      CompletedExchange(
        BuyerRole,
        Both(buyer = 2.BTC, seller = 2.003.BTC),
        Both(buyer = 100.EUR, seller = 98.EUR)
      )
    )) shouldBe Some(Price(60.EUR))
  }

  abstract class TestExchange extends Exchange {
    override val id = ExchangeId.random()
    override val log = ActivityLog.empty
    override val counterpartId = PeerId.random()
    override def lockTime = 0
    override def isStarted = true
    override def isCompleted = true
  }

  case class CompletedExchange(
      override val role: Role,
      override val exchangedBitcoin: Both[BitcoinAmount],
      override val exchangedFiat: Both[FiatAmount]) extends TestExchange {
    override def status = ExchangeStatus.Successful
    override def progress = Progress(exchangedBitcoin)
  }

  case class FailedExchange(
    override val role: Role,
    progressedBitcoin: Both[BitcoinAmount],
    override val exchangedBitcoin: Both[BitcoinAmount],
    override val exchangedFiat: Both[FiatAmount]) extends TestExchange {
    override def status = ExchangeStatus.Failed(FailureCause.StepFailed(3))
    override def progress = Progress(progressedBitcoin)
  }
}
