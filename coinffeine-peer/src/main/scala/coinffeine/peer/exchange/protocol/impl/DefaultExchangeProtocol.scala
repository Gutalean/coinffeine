package coinffeine.peer.exchange.protocol.impl

import scala.util.Try

import coinffeine.model.bitcoin.{Address, ImmutableTransaction}
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._
import coinffeine.peer.exchange.protocol._

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
