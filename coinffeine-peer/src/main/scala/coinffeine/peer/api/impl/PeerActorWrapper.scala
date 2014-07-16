package coinffeine.peer.api.impl

import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.util.Timeout

/** Base trait for classes building functionality around the peer actor */
private[impl] trait PeerActorWrapper {

  val peer: ActorRef

  /** Default timeout when asking things to the peer */
  implicit protected val timeout = Timeout(3.seconds)
}
