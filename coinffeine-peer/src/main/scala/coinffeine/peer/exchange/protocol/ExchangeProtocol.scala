package coinffeine.peer.exchange.protocol

import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.exchange._

trait ExchangeProtocol {

  /** Start a handshake for this exchange protocol.
    *
    * @param exchange        Exchange description
    * @param deposit         Multisigned deposit
    * @return                A new handshake
    */
  @throws[IllegalArgumentException]("when deposit funds are insufficient or incorrect")
  def createHandshake(exchange: DepositPendingExchange,
                                         deposit: ImmutableTransaction): Handshake

  /** Validate buyer and seller deposit transactions. */
  def validateDeposits(transactions: Both[ImmutableTransaction],
                       amounts: ActiveExchange.Amounts,
                       requiredSignatures: Both[PublicKey],
                       network: Network): Both[DepositValidation]

  /** Create a micro payment channel for an exchange given the deposit transactions and the
    * role to take.
    *
    * @param exchange   Exchange description
    */
  def createMicroPaymentChannel(exchange: RunningExchange): MicroPaymentChannel
}
