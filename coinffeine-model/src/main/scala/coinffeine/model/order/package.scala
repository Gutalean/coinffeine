package coinffeine.model

import coinffeine.model.currency.FiatCurrency

package object order {
  type AnyCurrencyActiveOrder = ActiveOrder[_ <: FiatCurrency]
  type AnyCurrencyOrder = Order[_ <: FiatCurrency]

  type AnyPrice = Price[_ <: FiatCurrency]
  type AnyOrderPrice = OrderPrice[_ <: FiatCurrency]
}
