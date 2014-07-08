package com.coinffeine.common.exchange.impl

import scala.util.Try

import com.coinffeine.common.FiatCurrency
import com.coinffeine.common.bitcoin.{Address, ImmutableTransaction}
import com.coinffeine.common.exchange._

private[impl] class DefaultExchangeProtocol extends ExchangeProtocol {

  override def createHandshake[C <: FiatCurrency](
      exchange: HandshakingExchange[C],
      unspentOutputs: Seq[UnspentOutput],
      changeAddress: Address): Handshake[C] = {
    val availableFunds = TransactionProcessor.valueOf(unspentOutputs.map(_.output))
    val depositAmount = exchange.role.myDepositAmount(exchange.amounts)
    require(availableFunds >= depositAmount,
      s"Expected deposit with $depositAmount ($availableFunds given)")
    val myDeposit = ImmutableTransaction {
      TransactionProcessor.createMultiSignedDeposit(
        unspentOutputs.map(_.toTuple), depositAmount, changeAddress,
        exchange.requiredSignatures.toSeq, exchange.parameters.network)
    }
    new DefaultHandshake(exchange, myDeposit)
  }

  override def createMicroPaymentChannel[C <: FiatCurrency](exchange: RunningExchange[C]) =
    new DefaultMicroPaymentChannel(exchange)

  override def validateCommitments(transactions: Both[ImmutableTransaction],
                                   amounts: Exchange.Amounts[FiatCurrency]): Try[Unit] = for {
    requiredSignatures <- DepositValidator.validateRequiredSignatures(transactions)
    validator = new DepositValidator(amounts, requiredSignatures)
    _ <- validator.requireValidBuyerFunds(transactions.buyer)
    _ <- validator.requireValidSellerFunds(transactions.seller)
  } yield ()

  override def validateDeposits(transactions: Both[ImmutableTransaction],
                                exchange: HandshakingExchange[FiatCurrency]) =
    new DepositValidator(exchange.amounts, exchange.requiredSignatures).validate(transactions)
}

object DefaultExchangeProtocol {
  trait Component extends ExchangeProtocol.Component {
    override lazy val exchangeProtocol = new DefaultExchangeProtocol
  }
}
