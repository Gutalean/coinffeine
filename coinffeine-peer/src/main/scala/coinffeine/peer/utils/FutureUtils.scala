package coinffeine.peer.utils

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

import com.google.common.util.concurrent.{FutureCallback, Futures, ListenableFuture}

trait FutureUtils {

  implicit class PimpMyFuture[A](future: Future[A]) {

    def materialize(implicit ec: ExecutionContext): Future[Try[A]] =
      future.map(Success.apply).recover {
        case NonFatal(ex) => Failure(ex)
      }
  }

  implicit class PimpMyGuavaFuture[A](future: ListenableFuture[A]) {

    def toScala: Future[A] = {
      val promise = Promise[A]()
      Futures.addCallback(future, new FutureCallback[A] {
        override def onFailure(cause: Throwable): Unit = promise.failure(cause)
        override def onSuccess(result: A): Unit = promise.success(result)
      })
      promise.future
    }
  }
}

object FutureUtils extends FutureUtils
