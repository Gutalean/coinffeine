package coinffeine.peer.exchange.protocol

import scala.util.Try
import scalaz.ValidationNel

import coinffeine.model.bitcoin._
import coinffeine.model.currency.{Bitcoin, FiatCurrency}
import coinffeine.model.exchange._

trait ExchangeProtocol {

  /** Start a handshake for this exchange protocol.
    *
    * @param exchange        Exchange description
    * @param deposit         Multisigned deposit
    * @return                A new handshake
    */
  @throws[IllegalArgumentException]("when deposit funds are insufficient or incorrect")
  def createHandshake[C <: FiatCurrency](exchange: DepositPendingExchange[C],
                                         deposit: ImmutableTransaction): Handshake[C]

  /** Validate buyer and seller deposit transactions. */
  def validateDeposits(transactions: Both[ImmutableTransaction],
                       amounts: Exchange.Amounts[_ <: FiatCurrency],
                       requiredSignatures: Both[PublicKey],
                       network: Network): Both[Try[Unit]]

  /** Create a micro payment channel for an exchange given the deposit transactions and the
    * role to take.
    *
    * @param exchange   Exchange description
    */
  def createMicroPaymentChannel[C <: FiatCurrency](exchange: RunningExchange[C]): MicroPaymentChannel[C]
}

object ExchangeProtocol {
  trait Component {
    def exchangeProtocol: ExchangeProtocol
  }

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
    override def description = "the output in in multisig with %s while %s were expected".format(
      actual.mkString("[", ", ", "]"),
      expected.toSeq.mkString("[", ", ", "]")
    )
  }
  case class InvalidCommittedAmount(actual: Bitcoin.Amount, expected: Bitcoin.Amount)
    extends DepositValidationError {
    override def description = s"committed a deposit of $actual when $expected was expected"
  }

  type DepositValidation = ValidationNel[DepositValidationError, Unit]
}
