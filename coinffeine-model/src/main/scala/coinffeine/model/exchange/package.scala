package coinffeine.model

import scalaz.ValidationNel

import coinffeine.model.currency.FiatCurrency

package object exchange {
  type AnyExchange = Exchange[_ <: FiatCurrency]
  type DepositValidation = ValidationNel[DepositValidationError, Unit]
}
