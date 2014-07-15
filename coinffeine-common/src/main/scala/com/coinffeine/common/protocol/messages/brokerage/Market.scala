package com.coinffeine.common.protocol.messages.brokerage

import coinffeine.model.currency.FiatCurrency

/** Identifies a given market. */
case class Market[+C <: FiatCurrency](currency: C)
