package coinffeine.model

import coinffeine.model.currency.FiatCurrency

package object exchange {
  type AnyExchange = Exchange[_ <: FiatCurrency]
}
