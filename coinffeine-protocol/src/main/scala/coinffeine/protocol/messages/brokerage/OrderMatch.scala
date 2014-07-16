package coinffeine.protocol.messages.brokerage

import coinffeine.model.currency.{BitcoinAmount, FiatAmount}
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.market.OrderId
import coinffeine.model.network.PeerId
import coinffeine.protocol.messages.PublicMessage

/** Represents a coincidence of desires of both a buyer and a seller */
case class OrderMatch(
    orderId: OrderId,
    exchangeId: ExchangeId,
    amount: BitcoinAmount,
    price: FiatAmount,
    counterpart: PeerId
) extends PublicMessage
