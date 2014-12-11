package coinffeine.common

import scala.concurrent.Future
import scala.language.implicitConversions

trait ScalaFutureImplicits {
  implicit def pimpScalaFuture[A](future: Future[A]): PimpScalaFuture[A] =
    new PimpScalaFuture(future)
}

object ScalaFutureImplicits extends ScalaFutureImplicits
