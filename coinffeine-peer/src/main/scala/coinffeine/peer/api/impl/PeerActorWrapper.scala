package coinffeine.peer.api.impl

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

import akka.actor.ActorRef
import akka.util.Timeout

/** Base trait for classes building functionality around the peer actor */
private[impl] trait PeerActorWrapper {

  val peer: ActorRef

  /** Default timeout when asking things to the peer */
  implicit protected val timeout = Timeout(3.seconds)

  protected def await[T](future: Future[T]): T = Await.result(future, timeout.duration)
}
