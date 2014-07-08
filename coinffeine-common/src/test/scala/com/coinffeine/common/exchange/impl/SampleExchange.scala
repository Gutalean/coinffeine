package com.coinffeine.common.exchange.impl

import com.coinffeine.common.PeerConnection
import com.coinffeine.common.Currency.Implicits._
import com.coinffeine.common.bitcoin.KeyPair
import com.coinffeine.common.exchange._
import com.coinffeine.common.network.NetworkComponent

trait SampleExchange { this: NetworkComponent =>

  val exchange = CompleteExchange(
    id = Exchange.Id("id"),
    amounts = Exchange.Amounts(
      bitcoinAmount = 1.BTC,
      fiatAmount = 1000.EUR,
      breakdown = Exchange.StepBreakdown(SampleExchange.IntermediateSteps)
    ),
    parameters = Exchange.Parameters(lockTime = 10, network),
    connections = Both(buyer = PeerConnection("buyer"), seller = PeerConnection("seller")),
    participants = Both(
      buyer = Exchange.PeerInfo("buyerAccount", new KeyPair()),
      seller = Exchange.PeerInfo("sellerAccount", new KeyPair())
    ),
    broker = Exchange.BrokerInfo(PeerConnection("broker"))
  )

  val buyerExchange = HandshakingExchange(BuyerRole, exchange.participants.buyer,
    exchange.participants.seller, exchange)

  val sellerExchange = HandshakingExchange(SellerRole, exchange.participants.seller,
    exchange.participants.buyer, exchange)
}

object SampleExchange {
  val IntermediateSteps = 10
}
