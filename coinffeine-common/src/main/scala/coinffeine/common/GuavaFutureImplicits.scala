package coinffeine.common

import scala.language.implicitConversions

import com.google.common.util.concurrent.ListenableFuture

trait GuavaFutureImplicits {
  implicit def pimpGuavaFuture[A](future: ListenableFuture[A]): PimpGuavaFuture[A] =
    new PimpGuavaFuture(future)
}

object GuavaFutureImplicits extends GuavaFutureImplicits
