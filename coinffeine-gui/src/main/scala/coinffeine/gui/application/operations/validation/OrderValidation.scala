package coinffeine.gui.application.operations.validation

import scalaz.syntax.validation._
import scalaz.{NonEmptyList, Semigroup, Validation}

import coinffeine.model.market.Spread
import coinffeine.model.order.OrderRequest

/** Checks order pre-requirements */
trait OrderValidation {
  /** Check a new order for its creation requirements */
  def apply(request: OrderRequest, spread: Spread): OrderValidation.Result
}

object OrderValidation {

  type Result = Validation[Problem, Unit]

  val Ok: Result = ().success

  def error(violation1: String, violations: String*) =
    Error(NonEmptyList(violation1, violations:_*)).failure

  def warning(violation1: String, violations: String*) =
    Warning(NonEmptyList(violation1, violations:_*)).failure

  sealed trait Problem

  object Problem {
    implicit object ProblemSemigroup extends Semigroup[Problem] {
      override def append(left: Problem, right: => Problem): Problem = (left, right) match {
        case (Warning(violations1), Warning(violations2)) =>
          Warning(violations1.append(violations2))
        case (Error(violations1), Error(violations2)) => Error(violations1.append(violations2))
        case (error: Error, _) => error
        case (_, error: Error) => error
      }
    }
  }

  /** At least one mandatory requirement was unmet and the order cannot be created */
  case class Error(violations: NonEmptyList[String]) extends Problem

  /** Optional requirements were unmet, the order can be created after user confirmation */
  case class Warning(violations: NonEmptyList[String]) extends Problem
}
