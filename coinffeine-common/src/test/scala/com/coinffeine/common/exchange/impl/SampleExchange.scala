package com.coinffeine.common.exchange.impl

import com.coinffeine.common.PeerConnection
import com.coinffeine.common.Currency.Implicits._
import com.coinffeine.common.bitcoin.KeyPair
import com.coinffeine.common.exchange._
import com.coinffeine.common.network.NetworkComponent

trait SampleExchange { this: NetworkComponent =>

  val participants = Both(
    buyer = Exchange.PeerInfo("buyerAccount", new KeyPair()),
    seller = Exchange.PeerInfo("sellerAccount", new KeyPair())
  )

  val exchange = NonStartedExchange(
    id = Exchange.Id("id"),
    amounts = Exchange.Amounts(
      bitcoinAmount = 1.BTC,
      fiatAmount = 1000.EUR,
      breakdown = Exchange.StepBreakdown(SampleExchange.IntermediateSteps)
    ),
    parameters = Exchange.Parameters(lockTime = 10, network),
    connections = Both(buyer = PeerConnection("buyer"), seller = PeerConnection("seller")),
    broker = Exchange.BrokerInfo(PeerConnection("broker"))
  )

  val buyerExchange =
    HandshakingExchange(BuyerRole, participants.buyer, participants.seller, exchange)

  val sellerExchange =
    HandshakingExchange(SellerRole, participants.seller, participants.buyer, exchange)
}

object SampleExchange {
  val IntermediateSteps = 10
}
