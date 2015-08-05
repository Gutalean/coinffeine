package coinffeine.peer.api.impl

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

import akka.util.Timeout

/** A trait that provides default await configuration.
  *
  * This is specially useful to interact with actors.
  */
private[impl] trait DefaultAwaitConfig {

  /** Default timeout when asking things to the peer */
  implicit protected val timeout = Timeout(3.seconds)

  protected def await[T](future: Future[T]): T = Await.result(future, timeout.duration)
}
