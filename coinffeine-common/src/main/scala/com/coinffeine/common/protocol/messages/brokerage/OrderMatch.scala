package com.coinffeine.common.protocol.messages.brokerage

import com.coinffeine.common.{BitcoinAmount, FiatAmount}
import com.coinffeine.common.exchange.{Both, Exchange, PeerId}
import com.coinffeine.common.protocol.messages.PublicMessage

/** Represents a coincidence of desires of both a buyer and a seller */
case class OrderMatch(
    exchangeId: Exchange.Id,
    amount: BitcoinAmount,
    price: FiatAmount,
    peers: Both[PeerId]
) extends PublicMessage
