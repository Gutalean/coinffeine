package coinffeine.protocol.messages.brokerage

import coinffeine.model.Both
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.market.OrderId
import coinffeine.model.network.PeerId
import coinffeine.protocol.messages.PublicMessage

/** Represents a coincidence of desires of both a buyer and a seller
  *
  * @constructor
  * @param orderId        Order identifier
  * @param exchangeId     Exchange identifier
  * @param bitcoinAmount  Gross bitcoin amount for the seller and net amount for the buyer
  * @param fiatAmount     Gross fiat amount for the buyer and net amount for the seller
  * @param lockTime       Block in which deposits will be redeemable
  * @param counterpart    Who is the exchange counterpart
  */
case class OrderMatch[C <: FiatCurrency](
    orderId: OrderId,
    exchangeId: ExchangeId,
    bitcoinAmount: Both[Bitcoin.Amount],
    fiatAmount: Both[CurrencyAmount[C]],
    lockTime: Long,
    counterpart: PeerId
) extends PublicMessage {
  def currency: C = fiatAmount.buyer.currency
}
