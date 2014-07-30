package coinffeine.model.exchange

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.network.PeerId

case class NonStartedExchange[+C <: FiatCurrency](
    override val id: ExchangeId,
    override val role: Role,
    override val counterpartId: PeerId,
    override val amounts: Exchange.Amounts[C],
    override val parameters: Exchange.Parameters,
    override val brokerId: PeerId) extends Exchange[C] {

  override val progress = Exchange.noProgress(amounts.currency)
}
