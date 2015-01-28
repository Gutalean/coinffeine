package coinffeine.common.akka

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.control.{NoStackTrace, NonFatal}

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

    def withReply[R]()(implicit timeout: Timeout,
                       resultType: ClassTag[R],
                       executor: ExecutionContext): Future[R] =
      wrapErrors((to ? request).mapTo[R])

    def withImmediateReply[R]()(implicit resultType: ClassTag[R],
                                executor: ExecutionContext): Future[R] =
      withReply()(ImmediateResponseTimeout, resultType, executor)

    def withReplyOrError[R, E](causeExtractor: E => Throwable = defaultCauseExtractor _)
                              (implicit timeout: Timeout,
                               resultType: ClassTag[R],
                               errorType: ClassTag[E],
                               executor: ExecutionContext): Future[R] =
      wrapErrors((to ? request).mapTo[Any].map {
        case resultType(success) => success
        case errorType(error) => throw causeExtractor(error)
      })

    def withImmediateReplyOrError[R, E](causeExtractor: E => Throwable = defaultCauseExtractor _)
                                       (implicit resultType: ClassTag[R],
                                        errorType: ClassTag[E],
                                        executor: ExecutionContext): Future[R] =
      withReplyOrError(causeExtractor)(ImmediateResponseTimeout, resultType, errorType, executor)

    /** Wrap errors to have a easier to diagnose message */
    private def wrapErrors[R](f: Future[R])
                             (implicit timeout: Timeout, executor: ExecutionContext): Future[R] =
      f.recoverWith {
        case cause: AskTimeoutException =>
          val detailedMessage = errorMessage +
            s": timeout of ${timeout.duration} waiting for response (${cause.getMessage})"
          Future.failed(new RuntimeException(detailedMessage) with NoStackTrace)
        case NonFatal(cause) =>
          Future.failed(new RuntimeException(errorMessage, cause))
      }

    private def defaultCauseExtractor(error: Any): Throwable = {
      new RuntimeException(s"actor replied with error message: $error")
    }
  }
}
