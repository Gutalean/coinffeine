package coinffeine.peer.exchange.protocol.impl

import scala.util.Try

import coinffeine.model.bitcoin._
import coinffeine.model.currency.{Bitcoin, FiatCurrency}
import coinffeine.model.exchange.{Both, Exchange}

private[impl] class DepositValidator(amounts: Exchange.Amounts[_ <: FiatCurrency],
                                     requiredSignatures: Both[PublicKey]) {

  def validate(transactions: Both[ImmutableTransaction]): Both[Try[Unit]] = Both(
    buyer = requireValidBuyerFunds(transactions.buyer),
    seller = requireValidSellerFunds(transactions.seller)
  )

  def requireValidBuyerFunds(transaction: ImmutableTransaction): Try[Unit] = Try {
    val buyerFunds = transaction.get.getOutput(0)
    requireValidMultisignature(buyerFunds)
    require(
      Bitcoin.fromSatoshi(buyerFunds.getValue) == amounts.deposits.buyer.output,
      "The amount of committed funds by the buyer does not match the expected amount")
  }

  def requireValidSellerFunds(transaction: ImmutableTransaction): Try[Unit] = Try {
    val sellerFunds = transaction.get.getOutput(0)
    requireValidMultisignature(sellerFunds)
    require(
      Bitcoin.fromSatoshi(sellerFunds.getValue) == amounts.deposits.seller.output,
      "The amount of committed funds by the seller does not match the expected amount")
  }

  private def requireValidMultisignature(funds: MutableTransactionOutput): Unit = {
    val MultiSigInfo(possibleKeys, requiredKeyCount) = MultiSigInfo.fromScript(funds.getScriptPubKey)
      .getOrElse(throw new IllegalArgumentException(
        "Transaction with funds is invalid because is not sending the funds to a multisig"))
    require(requiredKeyCount == 2, "Funds are sent to a multisig that do not require 2 keys")
    require(possibleKeys == requiredSignatures.toSeq,
      "Possible keys in multisig script does not match the expected keys")
  }
}
