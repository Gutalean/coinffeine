package com.coinffeine.common

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.payment.Payment

package object paymentprocessor {

  type AnyPayment = Payment[_ <: FiatCurrency]
}
