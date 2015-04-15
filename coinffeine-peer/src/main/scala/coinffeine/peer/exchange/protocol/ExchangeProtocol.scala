package coinffeine.peer.exchange.protocol

import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.currency.FiatCurrency
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
                       network: Network): Both[DepositValidation]

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
