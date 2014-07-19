package coinffeine.protocol.gateway

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, CanAwait, TimeoutException, Future}
import scala.util.{Success, Failure, Try}

import net.tomp2p.futures.{BaseFutureListener, BaseFuture}

package object protorpc {

  val BufferSize = 1048576
  val ConnectionTimeout = 10000

  implicit class PimpMyTomFuture[+T <: BaseFuture](future: T) extends Future[T] {
    override def onComplete[U](f: Try[T] => U)(implicit executor: ExecutionContext): Unit = {
      future.addListener(new BaseFutureListener[T] {
        override def exceptionCaught(t: Throwable): Unit =
          executor.execute(new Runnable {
            override def run(): Unit = f(Failure(t))
          })

        override def operationComplete(future: T): Unit = {
          val result =
            if (future.isFailed) Failure(new RuntimeException(future.getFailedReason))
            else Success(future)
          executor.execute(new Runnable {
            override def run(): Unit = f(result)
          })
        }
      })
    }

    override def isCompleted: Boolean = future.isCompleted

    override def value: Option[Try[T]] = if (!isCompleted) None else Some(Success(future))

    @scala.throws[InterruptedException](classOf[InterruptedException])
    @scala.throws[TimeoutException](classOf[TimeoutException])
    override def ready(atMost: Duration)(implicit permit: CanAwait): this.type = this

    @scala.throws[Exception](classOf[Exception])
    override def result(atMost: Duration)(implicit permit: CanAwait): T = future
  }
}
