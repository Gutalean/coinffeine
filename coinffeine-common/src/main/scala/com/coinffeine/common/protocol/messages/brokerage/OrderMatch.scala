package com.coinffeine.common.protocol.messages.brokerage

import com.coinffeine.common.{BitcoinAmount, FiatAmount, OrderId}
import com.coinffeine.common.exchange.{Both, Exchange, PeerId}
import com.coinffeine.common.protocol.messages.PublicMessage

/** Represents a coincidence of desires of both a buyer and a seller */
case class OrderMatch(
    orderId: OrderId,
    exchangeId: Exchange.Id,
    amount: BitcoinAmount,
    price: FiatAmount,
    counterpart: PeerId
) extends PublicMessage
