package coinffeine.common.akka

import scala.concurrent.{Future, ExecutionContext}
import scala.util.Failure

import akka.actor.ActorRef
import akka.util.Timeout

import coinffeine.common.ScalaFutureImplicits

/** Messages and utilities to communicate with a service embodied in an actor. */
object Service extends ScalaFutureImplicits {

  /** A message requesting the service actor to start.
    *
    * This message can be sent to a service actor in order to request it to start. It will respond
    * with [[Started]] after a successful start, or [[StartFailure]] if something went wrong.
    *
    * @param args   The arguments passed to the [[ServiceLifecycle.onStart()]] function
    */
  case class Start(args: Any)

  /** A response message indicating the service actor was successfully started. */
  case object Started

  /** A response message indicating the service actor failed to start. */
  case class StartFailure(cause: Throwable)

  /** A message requesting the service actor to stop.
    *
    * This message can be sent to a service actor in order to request it to stop. It will
    * respond with [[Stopped]] after a successful stop, or [[StopFailure]] if something went wrong.
    */
  case object Stop

  /** A response message indicating the service actor was successfully stopped. */
  case object Stopped

  /** A response message indicating the service actor failed to stop. */
  case class StopFailure(cause: Throwable)

  /** Ask a service actor to start with empty arguments.
    *
    * @param to       The service actor who is asked to start
    * @param timeout  The timeout for considering the start as failed
    * @return         A future representing the service start
    */
  def askStart(to: ActorRef)(implicit timeout: Timeout, executor: ExecutionContext): Future[Unit] =
    askStart(to, {})

  /** Ask a service actor to start.
    *
    * @param to       The service actor who is asked to start
    * @param args     The start arguments
    * @param timeout  The timeout for considering the start as failed
    * @return         A future representing the service start
    */
  def askStart[Args](to: ActorRef, args: Args)
                    (implicit timeout: Timeout, executor: ExecutionContext): Future[Unit] =
    AskPattern(to, Start(args))
      .withReplyOrError[Started.type, StartFailure](_.cause)
      .map(ignoreResult)

  /** Ask a service actor to stop.
    *
    * @param to       The service actor who is asked to stop
    * @param timeout  The timeout for considering the stop as failed
    * @return         A future representing the service stop
    */
  def askStop(to: ActorRef)(implicit timeout: Timeout, executor: ExecutionContext): Future[Unit] =
    AskPattern(to, Stop)
      .withReplyOrError[Stopped.type, StopFailure](_.cause)
      .map(ignoreResult)

  case class ParallelServiceStopFailure(stopFailures: Map[ActorRef, Throwable])
    extends Exception(s"Cannot stop some services: $stopFailures", stopFailures.values.head)

  /** Ask a number of services to stop in parallel.
    *
    * @param services  The service actors to stop
    * @param timeout   The timeout for considering the stop as failed
    * @return          A future representing the services stop
    */
  def askStopAll(services: ActorRef*)
                (implicit timeout: Timeout, executor: ExecutionContext): Future[Unit] = {
    val stopActions = services.map(askStop).map(_.materialize)
    for {
      results <- Future.sequence(stopActions)
    } yield {
      if (results.exists(_.isFailure)) {
        val errors = services.zip(results).collect {
          case (service, Failure(error)) => service -> error
        }
        throw new ParallelServiceStopFailure(errors.toMap)
      }
    }
  }

  private def ignoreResult[T](ignored: T) = {}
}
