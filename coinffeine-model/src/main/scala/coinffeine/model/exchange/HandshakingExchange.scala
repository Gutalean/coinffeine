package coinffeine.model.exchange

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.network.PeerId

/** Relevant information during the handshake of an exchange. This point of view is only held by
  * the parts as contains information not made public to everyone on the network. */
case class HandshakingExchange[+C <: FiatCurrency](
    override val role: Role,
    override val id: ExchangeId,
    override val amounts: Exchange.Amounts[C],
    override val blockedFunds: Exchange.BlockedFunds,
    override val parameters: Exchange.Parameters,
    override val counterpartId: PeerId,
    override val brokerId: PeerId,
    override val participants: Both[Exchange.PeerInfo]) extends OngoingExchange[C] {

  override val progress = Exchange.noProgress(amounts.currency)
}

object HandshakingExchange {
  def apply[C <: FiatCurrency](user: Exchange.PeerInfo, counterpart: Exchange.PeerInfo,
                               exchange: Exchange[C]): HandshakingExchange[C] = {
    import exchange._
    val participants = Both(
      buyer = role.buyer(user, counterpart),
      seller = role.seller(user, counterpart)
    )
    HandshakingExchange(
      role, id, amounts, blockedFunds, parameters, counterpartId, brokerId, participants)
  }
}
