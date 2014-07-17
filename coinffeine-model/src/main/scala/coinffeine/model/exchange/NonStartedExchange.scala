package coinffeine.model.exchange

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.network.PeerId

case class NonStartedExchange[+C <: FiatCurrency](
    override val id: ExchangeId,
    override val amounts: Exchange.Amounts[C],
    override val parameters: Exchange.Parameters,
    override val peerIds: Both[PeerId],
    override val brokerId: PeerId) extends Exchange[C] {

  override val progress = Exchange.noProgress(amounts.currency)
}
