package coinffeine.peer.exchange.protocol.impl

import scala.collection.JavaConverters._
import scalaz.{Scalaz, ValidationNel}

import coinffeine.model.bitcoin._
import coinffeine.model.currency._
import coinffeine.model.exchange.Exchange

/** Validates a refund transaction.
  *
  * TODO: add missing validations
  */
private class RefundTransactionValidation(parameters: Exchange.Parameters,
                                          expectedAmount: Bitcoin.Amount) {
  import Scalaz._
  import RefundTransactionValidation._

  def apply(refundTx: ImmutableTransaction): Result = {
    val tx = refundTx.get
    (requireSingleInput(tx) |@| requireValidRefundedAmount(tx) |@|  requireValidLockTime(tx)) { _ |+| _ |+| _ }
  }

  private def requireValidLockTime(tx: MutableTransaction): Result = {
    val actualLockTime = tx.isTimeLocked.option(tx.getLockTime)
    if (actualLockTime != Some(parameters.lockTime))
      InvalidLockTime(actualLockTime, parameters.lockTime).failNel
    else ().successNel
  }

  private def requireSingleInput(tx: MutableTransaction): Result = {
    val size = tx.getInputs.size
    if (size == 1) ().successNel
    else InvalidInputs(s"Just one input was expected but $size were found").failureNel
  }

  private def requireValidRefundedAmount(tx: MutableTransaction): Result = {
    val actualAmount = tx.getOutputs.asScala.foldLeft(Bitcoin.Zero)(_ + _.getValue)
    if (actualAmount == expectedAmount) ().successNel
    else InvalidRefundedAmount(actualAmount, expectedAmount).failureNel
  }
}

private object RefundTransactionValidation {
  type Result = ValidationNel[Error, Unit]

  sealed trait Error {
    def message: String
  }

  case class InvalidLockTime(actual: Option[Long], expected: Long) extends Error {
    override def message =
      "%d expected lock time but %s found".format(expected, actual.getOrElse("no lock time"))
  }

  case class InvalidInputs(cause: String) extends Error {
    override def message = s"invalid inputs: $cause"
  }

  case class InvalidRefundedAmount(actual: Bitcoin.Amount, expected: Bitcoin.Amount) extends Error {
    override def message = s"invalid refunded amount: $expected expected, $actual found"
  }
}
