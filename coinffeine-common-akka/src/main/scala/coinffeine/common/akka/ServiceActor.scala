package coinffeine.common.akka

import scala.util.control.NonFatal

import akka.actor._

import coinffeine.common.akka.Service._

/** An actor that can behave like a service.
  *
  * Actors implementing this trait will behave as a service that can be started and stopped. It
  * will provide its own implementation of [[Actor.receive()]] function that will process
  * start/stop messages sent to the actor, and will proceed consequently.
  *
  * Any actor implementing this thread must neither override [[Actor.receive()]] nor use
  * [[ActorContext.become()]] directly.
  *
  * @tparam Args The type of the arguments passed to the service in the [[Service.Start]] message.
  */
@deprecated("Use ServiceLifecycle instead")
trait ServiceActor[Args] { this: Actor =>

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
