package coinffeine.peer.exchange.protocol

import scala.util.Try

import coinffeine.model.bitcoin._
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.{Both, Exchange, HandshakingExchange, RunningExchange}

trait ExchangeProtocol {

  /** Start a handshake for this exchange protocol.
    *
    * @param exchange        Exchange description
    * @param unspentOutputs  Inputs for the deposit to create during the handshake
    * @param changeAddress   Address to return the excess of funds in unspentOutputs
    * @return                A new handshake
    */
  @throws[IllegalArgumentException]("when funds are insufficient")
  def createHandshake[C <: FiatCurrency](exchange: HandshakingExchange[C],
                                         unspentOutputs: Seq[UnspentOutput],
                                         changeAddress: Address): Handshake[C]

  /** Validate buyer and seller commitment transactions from the point of view of a broker */
  def validateCommitments(transactions: Both[ImmutableTransaction],
                          amounts: Exchange.Amounts[FiatCurrency]): Try[Unit]

  /** Validate buyer and seller deposit transactions. */
  def validateDeposits(transactions: Both[ImmutableTransaction],
                       exchange: HandshakingExchange[FiatCurrency]): Try[Exchange.Deposits]

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
}
