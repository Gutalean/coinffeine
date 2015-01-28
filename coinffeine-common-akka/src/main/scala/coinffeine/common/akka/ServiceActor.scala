package coinffeine.common.akka

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure
import scala.util.control.NonFatal

import akka.actor.{Actor, ActorRef}
import akka.util.Timeout

import coinffeine.common.ScalaFutureImplicits

/** An actor that can behave like a service.
  *
  * Actors implementing this trait will behave as a service that can be started and stopped. It
  * will provide its own implementation of [[Actor.receive()]] function that will process
  * start/stop messages sent to the actor, and will proceed consequently.
  *
  * Any actor implementing this thread must neither override [[Actor.receive()]] nor use
  * [[akka.actor.ActorContext.become()]] directly.
  *
  * @tparam Args The type of the arguments passed to the service in the
  *              [[coinffeine.common.akka.ServiceActor.Start]] message.
  */
trait ServiceActor[Args] { this: Actor =>

  import ServiceActor._

  private var requester: ActorRef = ActorRef.noSender

  override final def receive = receiveStart

  private def receiveStart: Receive = stopped orElse {
    case Start(args) =>
      try {
        requester = sender()
        become(starting(args.asInstanceOf[Args]))
      } catch {
        case ex: ClassCastException =>
          sender ! StartFailure(new IllegalArgumentException(s"Invalid start argument $args", ex))
        case NonFatal(ex) =>
          sender ! StartFailure(ex)
      }
    case Stop =>
      sender ! StopFailure(new IllegalStateException("cannot stop a non-started service"))
  }

  private def receiveStop(stopping: => Receive): Receive = {
    case Start(_) =>
      sender ! StartFailure(new IllegalStateException("cannot start an already started service"))
    case Stop =>
      try {
        requester = sender()
        become(stopping)
      } catch { case NonFatal(e) => sender ! StopFailure(e) }
  }

    /** Starting point of the service actor.
      *
      * This function defines the starting point of the service actor. Once a [[Start]] message
      * is received, the actor will use the [[Receive]] object resulting from this function to
      * process incoming messages. This [[Receive]] handler is meant to be overridden with an
      * implementation that performs the actions needed in order to start the service, i.e. tell
      * or ask to other actors (perhaps start some other services). Once start process has been
      * completed, the user code may invoke [[becomeStarted()]], which will fire a [[Started]]
      * message to the actor that requested the service to start, and then will set the
      * [[Receive]] object passed as argument as the new message handler.
      *
      * This is an example of how this function may be overridden to make the service interact
      * with some other actors in a starting phase.
      *
      * {{{
      *   override def starting(args: MyArgs) = {
      *     otherActor ! SomeMessage
      *     handle {
      *       case SomeResponse => someOtherActor ! SomeOtherMessage
      *       case SomeOtherMessage => becomeStarted(receivingMessages)
      *     }
      *   }
      * }}}
      *
      * We may use the starting function to perform synchronous initialization, as long as it
      * doesn't lock the calling thread too much time (remember: actors are meant to be
      * asynchronous), as the following example shows.
      *
      * {{{
      *   override def starting(args: MyArgs) = {
      *     initSomethingThatDoesNotTakeTooMuchTime();
      *     becomeStarted(receivingMessages)
      *   }
      * }}}
      *
      * Finally, we may provide a trivial implementation of the starting process if no
      * initialization is needed, as the following example shows.
      *
      * {{{
      *   override def starting(args: MyArgs) = { becomeStarted(receivingMessages) }
      * }}}
      *
      * But, in such case, perhaps you don't need a service actor at all ;-)
      */
  protected def starting(args: Args): Receive

  /** Stopping point of the service actor.
    *
    * Similar to [[starting()]], this function may be used to handle messages while stopping the
    * service. It's default implementation does nothing but pass immediately to the stopped state,
    * sending the [[Stopped]] message to the stop requester. It may be overridden to perform
    * a stopping process that involves telling or asking to other actors. Once the stop process
    * is completed, the user code must call to [[becomeStopped()]].
    */
  protected def stopping(): Receive = becomeStopped()

  /** Set a new behavior for the service.
    *
    * This function should be used instead of `context.become()` since it binds some [[Receive]]
    * functions to guarantee a correct start/stop behavior. Any direct use of `context.become()`
    * will lead to unexpected behaviour.
    *
    * @param r The [[Receive]] partial function to process incoming messages.
    */
  protected def become(r: Receive): Unit = { become(r, stopping()) }

  /** Set a new behavior for the service with a termination function.
    *
    * Same as `become(r: Receive)` but it provides a specific termination function for the new
    * behavior. If the service is stopped while in this behavior, the termination function passed
    * as argument will be invoked instead of `stop()`.
    *
    * @param r            The [[Receive]] partial function to process incoming messages
    * @param termination  The termination function to be invoked if service is requested to stop.
    */
  protected def become(r: Receive, termination: => Receive): Unit = {
    context.become(receiveStop(termination) orElse r)
  }

  /** Become the service in a started state.
    *
    * This function is meant to be called from the [[starting()]] message handler in order to
    * indicate that the service has been successfully started. The [[Receive]] object passed
    * as argument indicates the new state the service must become. Invoking this function when
    * the service is not starting would have a undetermined behaviour.
    *
    * @param r The [[Receive]] partial function to process incoming messages
    */
  protected def becomeStarted(r: Receive): Receive = becomeStarted(r, stopping())

  /** Become the service in a started state.
    *
    * This function is meant to be called from the [[starting()]] message handler in order to
    * indicate that the service has been successfully started. The [[Receive]] object passed
    * as argument indicates the new state the service must become. Invoking this function when
    * the service is not starting would have a undetermined behaviour.
    *
    * @param r            The [[Receive]] partial function to process incoming messages
    * @param termination  The termination function to be invoked if service is requested to stop
    */
  protected def becomeStarted(r: Receive, termination: => Receive): Receive = {
    requester ! Started
    requester = ActorRef.noSender
    become(r, termination)
    r
  }

  /** Become the service in a stopped state.
    *
    * This function is meant to be called from the [[stopping()]] message handler in order to
    * indicate that the service has been successfully stopped. Invoking this function when
    * the service is not starting would have a undetermined behaviour.
    */
  protected def becomeStopped(): Receive = {
    requester ! Stopped
    requester = ActorRef.noSender
    become(receive)
    receive
  }

  /** Behavior when stopped.
    *
    * This behavior is automatically used on the initial state and after an stop.
    * Its initial implementation does nothing.
    */
  protected def stopped: Receive = Map.empty

  /** Cancel the service start process.
    *
    * This will send a [[StartFailure]] to the actor that requested the service start. It's meant
    * to be used to report a start failure from the [[starting()]] state.
    */
  protected def cancelStart(cause: Throwable): Unit = {
    requester ! StartFailure(cause)
    requester = ActorRef.noSender
    become(receive)
  }
  /** Cancel the service stop process.
    *
    * This will send a [[StopFailure]] to the actor that requested the service stop. It's meant
    * to be used to report a stop failure from the [[stopping()]] state.
    */
  protected def cancelStop(cause: Throwable): Unit = {
    requester ! StopFailure(cause)
    requester = ActorRef.noSender
    become(receive)
  }

  /** A convenience function to assign a [[Receive]] type to a partial function. */
  protected def handle(r: Receive): Receive = r
}

object ServiceActor extends ScalaFutureImplicits {

  /** A message requesting the service actor to start.
    *
    * This message can be sent to a service actor in order to request it to start. It will respond
    * with [[Started]] after a successful start, or [[StartFailure]] if something went wrong.
    *
    * @param args   The arguments passed to the [[ServiceActor.starting()]] function
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
