package coinffeine.common.akka

import scala.util.{Success, Failure, Try}

import akka.actor._

/** Add service lifecycle behavior to an actor.
  *
  * Actors extending this trait will behave as a service that can be started and stopped. It
  * will provide its own implementation of [[Actor.receive()]] function that will process
  * start/stop messages sent to the actor, and will proceed consequently.
  *
  * Any implementation must neither override [[Actor.receive()]] nor use [[ActorContext.become()]]
  * directly.
  *
  * @tparam Args The type of the arguments passed to the service in the [[Service.Start]] message.
  */
trait ServiceLifecycle[Args] { this: Actor =>

  /** Function able to decide how to stop the service */
  type StopStrategy = () => StopTransition

  /** Whether a start transition completes immediately, spends some time in starting state or
    * it is cancelled. */
  protected sealed trait StartTransition
  protected case class BecomeStarted(behavior: Receive, onStop: StopStrategy = onStop)
    extends StartTransition
  protected case class BecomeStarting(behavior: Receive) extends StartTransition
  protected case class CancelStart(cause: Throwable) extends StartTransition

  /** Whether a stop transition completes immediately, spends some time in stopping state or it is
    * cancelled. */
  protected sealed trait StopTransition
  protected case object BecomeStopped extends StopTransition
  protected case class BecomeStopping(behavior: Receive) extends StopTransition
  protected case class CancelStop(cause: Throwable) extends StopTransition

  /** Starting point of the service actor.
    *
    * This function defines the starting point of the service actor. Once a [[Service.Start]]
    * is received, the result of this function will be used to determine what to do: cancel the
    * start, start right away or going to an starting state.
    *
    * The simplest behavior consist on become started directly. You can do that as follows:
    *
    * {{{
    *   override def onStart(args: MyArgs) = {
    *     otherActor ! SomeMessage
    *     BecomeStarted(processingMessages)
    *   }
    * }}}
    *
    * In the case of going to the starting state, the actor will continue that way until
    * [[completeStart()]] is called. For example:
    *
    * {{{
    *   override def onStart(args: MyArgs) = {
    *     otherActor ! SomeMessage
    *     BecomeStarting {
    *       case SomeResponse => completeStart(beingInitialized)
    *       case OtherResponse(ex) => cancelStart(ex)
    *     }
    *   }
    * }}}
    *
    * Cancelling the start is even simpler:
    *
    * {{{
    *   override def onStart(args: MyArgs) = CancelStart(new Exception("not ready"))
    * }}}
    *
    * @param args  Starting arguments
    * @return      How to transition from start to starting or started
    */
  protected def onStart(args: Args): StartTransition

  /** Stop handler.
    *
    * This behavior is used by default and it just stops the service right away.
    * You might want to override it to provide a different default behavior for all cases or
    * specify a custom one in [[become()]] or [[BecomeStarted]].
    */
  protected def onStop(): StopTransition = BecomeStopped

  /** Behavior when stopped.
    *
    * This behavior is automatically used on the initial state and after an stop.
    * Its initial implementation does nothing.
    */
  protected def stopped: Receive = Map.empty

  /** Transit from starting to started with a given behavior. */
  final protected def completeStart(behavior: Receive): Unit =
    state.completeStart(behavior, onStop)

  /** Transit from starting to started with a given behavior and stop handler. */
  final protected def completeStart(behavior: Receive, onStop: StopStrategy): Unit =
    state.completeStart(behavior, onStop)

  /** Cancel a start with an exception */
  final protected def cancelStart(cause: Throwable): Unit = state.cancelStart(cause)

  /** Transit from stopping to stop */
  final protected def completeStop(): Unit = state.completeStop()

  /** Cancel a stop with an exception */
  final protected def cancelStop(cause: Throwable): Unit = state.cancelStop(cause)

  /** When started, change the behavior. */
  final protected def become(behavior: Receive): Unit = state.become(behavior)

  /** When started, change the behavior and the stop handler */
  final protected def become(behavior: Receive, stopStrategy: StopStrategy): Unit = {
    state.become(behavior)
    state.updateStopStrategy(stopStrategy)
  }

  private var state: State = Stopped
  override final def receive: Receive = new Receive {
    override def isDefinedAt(message: Any) = state.receive.isDefinedAt(message)
    override def apply(message: Any) = state.receive.apply(message)
  }

  private sealed trait State {
    def receive: Receive
    def name: String

    def completeStart(receive: Receive, onStop: StopStrategy): Unit =
      illegalTransition("complete start")
    def cancelStart(cause: Throwable): Unit = illegalTransition("cancel start")
    def completeStop(): Unit = illegalTransition("complete stop")
    def cancelStop(cause: Throwable): Unit = illegalTransition("cancel stop")
    def become(behavior: Receive): Unit = illegalTransition("change behavior")
    def updateStopStrategy(onStop: StopStrategy): Unit = illegalTransition("change stop strategy")

    protected def notStarting: Receive = {
      case Service.Start(_) =>
        sender() ! Service.StartFailure(
          new IllegalStateException(s"cannot start while in $name state"))
    }

    protected def notStopping: Receive = {
      case Service.Stop =>
        sender() ! Service.StopFailure(
          new IllegalStateException(s"cannot stop while in $name state"))
    }

    private def illegalTransition(transition: String): Unit = {
      throw new IllegalStateException(s"cannot $transition while in $name state")
    }
  }

  private case object Stopped extends State {
    override val receive = notStopping orElse starting orElse stopped
    override val name = "stopped"

    private def starting: Receive = {
      case Service.Start(args) =>
        Try(onStart(args.asInstanceOf[Args])) match {
          case Failure(cause) => startFailure(cause)
          case Success(CancelStart(cause)) => startFailure(cause)
          case Success(BecomeStarted(behavior, onStop)) =>
            sender() ! Service.Started
            state = Started(behavior, onStop)
          case Success(BecomeStarting(behavior)) =>
            state = Starting(behavior, listener = sender())
        }
    }

    private def startFailure(cause: Throwable): Unit = {
      sender() ! Service.StartFailure(cause)
    }
  }

  private case class Starting(behavior: Receive, listener: ActorRef) extends State {
    override val receive = notStarting orElse notStopping orElse behavior
    override val name = "starting"

    override def cancelStart(cause: Throwable): Unit = {
      listener ! Service.StartFailure(cause)
      state = Stopped
    }

    override def completeStart(receive: Receive, onStop: StopStrategy): Unit = {
      listener ! Service.Started
      state = Started(receive, onStop)
    }
  }

  private case class Started(behavior: Receive, onStop: StopStrategy) extends State {
    override val receive = notStarting orElse stopping orElse behavior
    override val name = "started"

    override def become(behavior: Receive): Unit = {
      state = copy(behavior = behavior)
    }

    override def updateStopStrategy(onStop: StopStrategy): Unit = {
      state = copy(onStop = onStop)
    }

    private def stopping: Receive = {
      case Service.Stop =>
        Try(onStop()) match {
          case Failure(cause) => stopFailure(cause)
          case Success(CancelStop(cause)) => stopFailure(cause)
          case Success(BecomeStopped) =>
            sender() ! Service.Stopped
            state = Stopped
          case Success(BecomeStopping(stoppingBehavior)) =>
            state = Stopping(stoppingBehavior, prevState = this, listener = sender())
        }
    }

    private def stopFailure(cause: Throwable): Unit = {
      sender() ! Service.StopFailure(cause)
    }
  }

  private case class Stopping(behavior: Receive, prevState: Started, listener: ActorRef) extends State {
    override val receive = notStarting orElse notStopping orElse behavior
    override val name = "stopping"

    override def completeStop(): Unit = {
      listener ! Service.Stopped
      state = Stopped
    }

    override def cancelStop(cause: Throwable): Unit = {
      listener ! Service.StopFailure(cause)
      state = prevState
    }
  }
}
