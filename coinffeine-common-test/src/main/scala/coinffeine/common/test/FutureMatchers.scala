package coinffeine.common.test

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait FutureMatchers {

  def scaleFactor: Double

  implicit val defaultTimeout: FiniteDuration = 3.seconds

  implicit class PimpMyFuture[A](future: Future[A]) {
    def futureValue(implicit timeout: FiniteDuration): A =
      Await.result(future, timeout * scaleFactor)
  }

  def whenReady[A, B](future: Future[A])(block: A => B)(implicit timeout: FiniteDuration): B = {
    block(future.futureValue(timeout))
  }
}
