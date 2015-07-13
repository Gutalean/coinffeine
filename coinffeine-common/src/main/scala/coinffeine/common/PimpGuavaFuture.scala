package coinffeine.common

import scala.concurrent.{Future, Promise}

import com.google.common.util.concurrent.{FutureCallback, Futures, ListenableFuture}

class PimpGuavaFuture[A](val future: ListenableFuture[A]) extends AnyVal {
  def toScala: Future[A] = {
    val promise = Promise[A]()
    Futures.addCallback(future, new FutureCallback[A] {
      override def onFailure(cause: Throwable): Unit = promise.failure(cause)
      override def onSuccess(result: A): Unit = promise.success(result)
    })
    promise.future
  }
}
