package com.coinffeine.common.exchange

import scala.util.Try

import com.coinffeine.common.FiatCurrency
import com.coinffeine.common.bitcoin._

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
                       exchange: OngoingExchange[FiatCurrency]): Try[Exchange.Deposits]

  /** Create a micro payment channel for an exchange given the deposit transactions and the
    * role to take.
    *
    * @param exchange   Exchange description
    * @param role       Role played on the exchange
    * @param deposits   Already compromised deposits for buyer and seller
    */
  def createMicroPaymentChannel(exchange: OngoingExchange[FiatCurrency],
                                role: Role, deposits: Exchange.Deposits): MicroPaymentChannel
}

object ExchangeProtocol {
  trait Component {
    def exchangeProtocol: ExchangeProtocol
  }
}
