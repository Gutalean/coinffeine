package coinffeine.peer.exchange.protocol.impl

import scala.util.Try

import coinffeine.model.bitcoin._
import coinffeine.model.currency.{Bitcoin, FiatCurrency}
import coinffeine.model.exchange._

private[impl] class DepositValidator(amounts: Exchange.Amounts[_ <: FiatCurrency],
                                     requiredSignatures: Both[PublicKey],
                                     network: Network) {

  def validate(transactions: Both[ImmutableTransaction]): Both[Try[Unit]] = Both(
    buyer = requireValidBuyerFunds(transactions.buyer),
    seller = requireValidSellerFunds(transactions.seller)
  )

  def requireValidBuyerFunds(transaction: ImmutableTransaction): Try[Unit] =
    requireValidFunds(BuyerRole, transaction)

  def requireValidSellerFunds(transaction: ImmutableTransaction): Try[Unit] =
    requireValidFunds(SellerRole, transaction)

  private def requireValidFunds(role: Role, transaction: ImmutableTransaction): Try[Unit] = Try {
    val funds = transaction.get.getOutput(0)
    requireValidMultiSignature(funds)
    val committedFunds: Bitcoin.Amount = funds.getValue
    val expectedFunds = role.select(amounts.deposits).output
    require(
      committedFunds == expectedFunds,
      s"$committedFunds committed by the $role while $expectedFunds were expected")
  }

  private def requireValidMultiSignature(funds: MutableTransactionOutput): Unit = {
    funds match {
      case MultiSigOutput(MultiSigInfo(possibleKeys, requiredKeyCount)) =>
        require(requiredKeyCount == 2, "Funds are sent to a multisig that do not require 2 keys")
        require(possibleKeys.map(toAddress) == requiredSignatures.toSeq.map(toAddress),
          "Possible keys in multisig script does not match the expected keys")
      case _ =>
        throw new IllegalArgumentException(
          "Transaction with funds is invalid because is not sending the funds to a multisig")
    }
  }

  private def toAddress(key: PublicKey) = key.toAddress(network)
}
