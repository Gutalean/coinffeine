package coinffeine.common.akka

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.control.NonFatal

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout

/** Utility class for using the Akka ask pattern */
object AskPattern {

  /** Timeout used for requests that should be immediately responded (no external communication
    * or blocking is needed) */
  val ImmediateResponseTimeout = Timeout(1.second)

  def apply(to: ActorRef, request: Any): AskRequestBuilder =
    apply(to, request, s"Requesting $request to $to")

  def apply(to: ActorRef, request: Any, errorMessage: String): AskRequestBuilder =
    new AskRequestBuilder(to, request, errorMessage)

  class AskRequestBuilder private[AskPattern] (to: ActorRef, request: Any, errorMessage: String) {

    def withReply[R]()(implicit timeout: Timeout, ev: ClassTag[R], ec: ExecutionContext): Future[R] =
      (to ? request).mapTo[R].recoverWith {
        case NonFatal(cause) =>
          Future.failed(new RuntimeException(errorMessage, cause))
      }

    def withImmediateReply[R]()(implicit ev: ClassTag[R], ec: ExecutionContext): Future[R] =
      withReply()(ImmediateResponseTimeout, ev, ec)
  }
}
