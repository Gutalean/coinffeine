package coinffeine.model.exchange

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Exchange.{Amounts, Parameters, Progress}
import coinffeine.model.network.PeerId

/** An exchange that has been completed. */
case class CompletedExchange[+C <: FiatCurrency](
    override val id: ExchangeId,
    override val role: Role,
    override val counterpartId: PeerId,
    override val parameters: Parameters,
    override val brokerId: PeerId,
    override val amounts: Amounts[C]) extends Exchange[C] {
  override val progress = Progress(amounts.bitcoinAmount, amounts.fiatAmount)
}

object CompletedExchange {
  def fromExchange[C <: FiatCurrency](exchange: Exchange[C]): CompletedExchange[C] = {
    import exchange._
    CompletedExchange(id, role, counterpartId, parameters, brokerId, amounts)
  }
}
