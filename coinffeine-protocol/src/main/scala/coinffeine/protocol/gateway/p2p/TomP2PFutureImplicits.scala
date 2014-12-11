package coinffeine.protocol.gateway.p2p

import scala.concurrent._
import scala.language.implicitConversions
import scala.util.control.NoStackTrace

import net.tomp2p.futures.{BaseFuture, BaseFutureListener}

/** Adapter from TomP2P futures to Scala ones */
private trait TomP2PFutureImplicits {

  implicit def pimpTomP2PFuture[T <: BaseFuture](tomp2pFuture: T): Future[T] = {
    val promise = Promise[T]()
    tomp2pFuture.addListener(new BaseFutureListener[T] {
      override def exceptionCaught(cause: Throwable): Unit = {
        promise.failure(cause)
      }

      override def operationComplete(result: T): Unit = {
        if (result.isSuccess) promise.success(result)
        else promise.failure(new Exception(result.getFailedReason) with NoStackTrace)
      }
    })
    promise.future
  }
}

