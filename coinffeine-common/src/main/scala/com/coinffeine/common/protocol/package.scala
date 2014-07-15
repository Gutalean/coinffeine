package com.coinffeine.common

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}

package object protocol {
  type Spread[C <: FiatCurrency] = (Option[CurrencyAmount[C]], Option[CurrencyAmount[C]])
}
