package coinffeine.model.exchange

import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.currency.Bitcoin

sealed trait DepositValidationError {
  def description: String
}

case object NoOutputs extends DepositValidationError {
  override def description = "the transaction has no outputs"
}

case object NoMultiSig extends DepositValidationError {
  override def description = "transaction is not in multi signature at all"
}

case class UnexpectedNumberOfRequiredSignatures(requiredSignatures: Int)
  extends DepositValidationError {
  override def description = s"the output requires $requiredSignatures signatures instead of 2"
}

case class UnexpectedSignatureAddresses(actual: Seq[Address], expected: Both[Address])
  extends DepositValidationError {
  override def description = "the output is in multisig with %s while %s were expected".format(
    actual.mkString("[", ", ", "]"),
    expected.toSeq.mkString("[", ", ", "]")
  )
}

case class InvalidCommittedAmount(actual: Bitcoin.Amount, expected: Bitcoin.Amount)
  extends DepositValidationError {
  override def description = s"committed a deposit of $actual when $expected was expected"
}
