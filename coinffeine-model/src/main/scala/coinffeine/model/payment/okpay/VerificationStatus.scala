package coinffeine.model.payment.okpay

import coinffeine.model.currency._

sealed trait VerificationStatus {
  def periodicLimits: FiatAmounts
}

object VerificationStatus {

  val values = Set(NotVerified, Verified)

  case object NotVerified extends VerificationStatus {
    override def periodicLimits = FiatAmounts.fromAmounts(300.EUR, 300.USD)
  }

  case object Verified extends VerificationStatus {
    override def periodicLimits = FiatAmounts.fromAmounts(100000.EUR, 100000.USD)
  }

  def parse(status: String): Option[VerificationStatus] = values.find(_.toString == status)
}
