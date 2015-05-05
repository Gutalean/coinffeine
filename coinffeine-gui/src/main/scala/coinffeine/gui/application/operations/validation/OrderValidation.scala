package coinffeine.gui.application.operations.validation

import scalaz.NonEmptyList

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.{OrderRequest, Spread}

/** Checks order pre-requirements */
trait OrderValidation {
  /** Check a new order for its creation requirements */
  def apply[C <: FiatCurrency](request: OrderRequest[C], spread: Spread[C]): OrderValidation.Result
}

object OrderValidation {

  sealed trait Result

  /** All requirements were met and the order can be created right away */
  case object OK extends Result

  /** At least one mandatory requirement was unmet and the order cannot be created */
  case class Error(violations: NonEmptyList[String]) extends Result

  /** Optional requirements were unmet, the order can be created after user confirmation */
  case class Warning(violations: NonEmptyList[String]) extends Result

  object Result {
    def combine(checkResults: Result*): Result = {
      checkResults.foldLeft[Result](OK)(combine)
    }

    def combine(leftResult: Result, rightResult: Result): Result =
      (leftResult, rightResult) match {
        case (OK, other) => other
        case (other, OK) => other
        case (Warning(violations1), Warning(violations2)) =>
          Warning(violations1.append(violations2))
        case (Error(violations1), Error(violations2)) => Error(violations1.append(violations2))
        case (error: Error, _) => error
        case (_, error: Error) => error
      }
  }
}
