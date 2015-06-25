package coinffeine.model

import scalaz.ValidationNel

package object exchange {
  type DepositValidation = ValidationNel[DepositValidationError, Unit]
}
