package coinffeine.model

import coinffeine.model.currency.FiatCurrency

package object payment {

  type AnyPayment = Payment[_ <: FiatCurrency]
}
