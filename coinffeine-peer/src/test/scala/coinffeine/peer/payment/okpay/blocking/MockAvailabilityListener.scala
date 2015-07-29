package coinffeine.peer.payment.okpay.blocking

import org.scalatest.ShouldMatchers

import coinffeine.model.exchange.ExchangeId

class MockAvailabilityListener extends AvailabilityListener with ShouldMatchers {

  private var availability = Map.empty[ExchangeId, Boolean]

  override def onAvailable(funds: ExchangeId): Unit = {
    availability += funds -> true
  }

  override def onUnavailable(funds: ExchangeId): Unit = {
    availability += funds -> false
  }

  def expectAvailable(funds: ExchangeId): Unit = {
    expectAvailability(funds, expectedAvailability = true)
  }

  def expectUnavailable(funds: ExchangeId): Unit = {
    expectAvailability(funds, expectedAvailability = false)
  }

  private def expectAvailability(funds: ExchangeId, expectedAvailability: Boolean): Unit = {
    withClue(s"availability of $funds") {
      if (!availability.contains(funds)) {
        fail("availability was not notified")
      }
      availability(funds) shouldBe expectedAvailability
    }
  }
}
