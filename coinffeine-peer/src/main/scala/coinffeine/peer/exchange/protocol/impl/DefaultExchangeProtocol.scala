package coinffeine.peer.exchange.protocol.impl

import coinffeine.model.bitcoin.{ImmutableTransaction, PublicKey}
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

  override def validateDeposits(transactions: Both[ImmutableTransaction],
                                amounts: Exchange.Amounts[_ <: FiatCurrency],
                                requiredSignatures: Both[PublicKey]) =
    new DepositValidator(amounts, requiredSignatures).validate(transactions)
}

object DefaultExchangeProtocol {
  trait Component extends ExchangeProtocol.Component {
    override lazy val exchangeProtocol = new DefaultExchangeProtocol
  }
}
