package coinffeine.peer.exchange.protocol.impl

import scalaz.Scalaz._

import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.currency.{Bitcoin, FiatCurrency}
import coinffeine.model.exchange._

private[impl] class DepositValidator(amounts: Exchange.Amounts[_ <: FiatCurrency],
                                     requiredSignatures: Both[PublicKey],
                                     network: Network) {

  def validate(transactions: Both[ImmutableTransaction]): Both[DepositValidation] = Both(
    buyer = requireValidBuyerFunds(transactions.buyer),
    seller = requireValidSellerFunds(transactions.seller)
  )

  def requireValidBuyerFunds(transaction: ImmutableTransaction): DepositValidation =
    requireValidDeposit(BuyerRole, transaction)

  def requireValidSellerFunds(transaction: ImmutableTransaction): DepositValidation =
    requireValidDeposit(SellerRole, transaction)

  private def requireValidDeposit(role: Role,
                                  transaction: ImmutableTransaction): DepositValidation = {
    Option(transaction.get.getOutput(0)).fold[DepositValidation](NoOutputs.failureNel) { funds =>
      requireValidMultiSignature(funds) *> requireValidAmount(role, funds)
    }
  }

  private def requireValidAmount(role: Role, funds: MutableTransactionOutput): DepositValidation = {
    val actualFunds: Bitcoin.Amount = funds.getValue
    val expectedFunds = role.select(amounts.deposits).output
    if (actualFunds == expectedFunds) ().successNel
    else InvalidCommittedAmount(actualFunds, expectedFunds).failureNel
  }

  private def requireValidMultiSignature(funds: MutableTransactionOutput): DepositValidation = {
    funds match {
      case MultiSigOutput(MultiSigInfo(possibleKeys, requiredKeyCount)) =>
        requireTwoOutOfTwoSignatures(requiredKeyCount) *> requireExpectedAddresses(possibleKeys)
      case _ => NoMultiSig.failureNel
    }
  }

  private def requireTwoOutOfTwoSignatures(requiredKeyCount: Int): DepositValidation = {
    if (requiredKeyCount == 2) ().successNel
    else UnexpectedNumberOfRequiredSignatures(requiredKeyCount).failureNel
  }

  private def requireExpectedAddresses(actualKeys: Seq[PublicKey]): DepositValidation = {
    val actualAddresses = actualKeys.map(toAddress)
    val expectedAddresses = requiredSignatures.map(toAddress)
    if (actualAddresses == expectedAddresses.toSeq) ().successNel
    else UnexpectedSignatureAddresses(actualAddresses, expectedAddresses).failureNel
  }

  private def toAddress(key: PublicKey) = key.toAddress(network)
}
