package coinffeine.peer.utils

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class PimpScalaFuture[A](val future: Future[A]) extends AnyVal {

  /** Reifies failures so instead of dealing with a failed futures you get a value
    * wrapped on a Try.
    */
  def materialize(implicit ec: ExecutionContext): Future[Try[A]] =
    future.map(Success.apply).recover {
      case NonFatal(ex) => Failure(ex)
    }

  /** Guarantees than an action will be executed if the base future fails */
  def failureAction(action: => Future[_])(implicit executor: ExecutionContext): Future[A] =
    materialize.flatMap {
      case Success(result) => Future.successful(result)
      case Failure(error) => new PimpScalaFuture(action).materialize.map(_ => throw  error)
    }
}
