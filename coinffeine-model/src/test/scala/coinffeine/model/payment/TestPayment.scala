package coinffeine.model.payment

import scala.util.Random

import org.joda.time.DateTime

import coinffeine.model.currency._
import coinffeine.model.payment.PaymentProcessor._

case class TestPayment(
    override val paymentId: String,
    override val senderId: String,
    override val receiverId: String,
    override val netAmount: FiatAmount,
    override val fee: FiatAmount,
    override val date: DateTime,
    override val description: String,
    override val invoice: Invoice,
    override val completed: Boolean) extends Payment {
  require(netAmount.currency == fee.currency, s"Inconsistent currencies: $this")
}

object TestPayment {
  def random(
      paymentId: String = Random.nextInt(1000).toString,
      senderId: String = randomAccount(),
      receiverId: String = randomAccount(),
      netAmount: FiatAmount = randomEuros(10000) + 1.EUR,
      fee: FiatAmount = randomEuros(10000),
      date: DateTime = DateTime.now(),
      description: String = s"step ${Random.nextInt(50) + 1}/50",
      invoice: Invoice = if (Random.nextBoolean()) "invoice1" else "",
      completed: Boolean = Random.nextBoolean()): TestPayment =
    TestPayment(paymentId, senderId, receiverId, netAmount, fee, date, description, invoice,
      completed)

  private def randomAccount(): String = s"account${Random.nextInt(100)}"

  private def randomEuros(maxCents: Int) = Euro.fromUnits(Random.nextInt(maxCents))
}
