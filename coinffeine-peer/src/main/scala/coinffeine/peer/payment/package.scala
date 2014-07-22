package coinffeine.peer

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.payment.Payment

package object payment {

  type AnyPayment = Payment[FiatCurrency]
}
