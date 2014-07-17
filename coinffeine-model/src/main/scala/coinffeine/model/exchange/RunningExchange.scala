package coinffeine.model.exchange

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.network.PeerId

case class RunningExchange[+C <: FiatCurrency](
    override val role: Role,
    override val id: ExchangeId,
    override val amounts: Exchange.Amounts[C],
    override val parameters: Exchange.Parameters,
    override val peerIds: Both[PeerId],
    override val brokerId: PeerId,
    override val participants: Both[Exchange.PeerInfo],
    deposits: Exchange.Deposits,
    override val progress: Exchange.Progress[C]) extends OngoingExchange[C] {

  require(progress.bitcoinsTransferred <= amounts.bitcoinAmount,
    "invalid running exchange instantiation: " +
      s"progress $progress is inconsistent with amounts $amounts")
}

object RunningExchange {

  def apply[C <: FiatCurrency](deposits: Exchange.Deposits,
                               exchange: HandshakingExchange[C]): RunningExchange[C] = {
    import exchange._
    RunningExchange(
      role, id, amounts, parameters, peerIds, brokerId, participants, deposits, progress)
  }
}
