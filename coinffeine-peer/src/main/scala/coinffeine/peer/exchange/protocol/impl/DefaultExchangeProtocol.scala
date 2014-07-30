package coinffeine.peer.exchange.protocol.impl

import scala.util.Try

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._
import coinffeine.peer.exchange.protocol._

private[impl] class DefaultExchangeProtocol extends ExchangeProtocol {

  override def createHandshake[C <: FiatCurrency](
      exchange: HandshakingExchange[C],
      deposit: ImmutableTransaction): Handshake[C] = {
    requireValidDeposit(exchange, deposit)
    new DefaultHandshake(exchange, deposit)
  }

  private def requireValidDeposit[C <: FiatCurrency](exchange: HandshakingExchange[C],
                                                     deposit: ImmutableTransaction): Unit = {
    val validator = new DepositValidator(exchange.amounts, exchange.participants.map(_.bitcoinKey))
    val validation = exchange.role match {
      case BuyerRole => validator.requireValidBuyerFunds _
      case SellerRole => validator.requireValidSellerFunds _
    }
    validation(deposit).get
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
