package coinffeine.common.akka

import scala.concurrent.duration.FiniteDuration

import akka.actor._

/** And actor that acts as a register of service actors.
  *
  * This actor let's other actors to register their services with the purpose of being
  * located by other actors. It can be used as a mechanism in order to resolve actor dependencies.
  */
class ServiceRegistryActor extends Actor with ActorLogging {
  import ServiceRegistryActor._

  private var regs: Map[ServiceId, ActorRef] = Map.empty
  private var holding: Map[ServiceId, Set[(ActorRef, Cancellable)]] = Map.empty

  override def receive = {
    case RegisterService(id, actor) =>
      regs += id -> actor
      releaseHolding(id, actor)
    case LocateService(id) if regs.contains(id) =>
      sender ! ServiceLocated(id, regs(id))
    case LocateService(id) =>
      sender ! ServiceNotFound(id)
    case EventuallyLocateService(id, _) if regs.contains(id) =>
      sender ! ServiceLocated(id, regs(id))
    case EventuallyLocateService(id, timeout) =>
      implicit val executor = context.dispatcher
      val requester = sender()
      val timer = context.system.scheduler.scheduleOnce(timeout)(triggerTimeout(id, requester))
      addTimer(id, requester, timer)
  }

  private def addTimer(id: ServiceId, requester: ActorRef, timer: Cancellable): Unit = {
    val prevTimers = holding.getOrElse(id, Set.empty)
    holding += id -> (prevTimers + (requester -> timer))
  }

  private def triggerTimeout(id: ServiceId, requester: ActorRef): Unit = {
    val prevTimers = holding.getOrElse(id, Set.empty)
    holding += id -> prevTimers.filterNot { case (req, timer) => req == requester }
    requester ! ServiceNotFound(id)
  }

  private def releaseHolding(id: ServiceId, service: ActorRef): Unit = {
    holding.getOrElse(id, Set.empty).foreach { case (req, timer) =>
      timer.cancel()
      req ! ServiceLocated(id, service)
    }
    holding -= id
  }
}

object ServiceRegistryActor {

  /** An object that identifies a determined service provided by an actor. */
  case class ServiceId(id: String) {
    override def toString = s"actor service $id"
  }

  /** A request to register a service provided by the given actor.
    *
    * If there was a service already registered with the same ID, the former registration is
    * overwritten.
    *
    * @param id     The identifier of the service to be registered
    * @param actor  The actor that provides that service
    */
  case class RegisterService(id: ServiceId, actor: ActorRef)

  /** A request to locate a service with the given ID.
    *
    * This will be followed by a [[ServiceLocated]] message if there was a match, or
    * [[ServiceNotFound]] if there is no service for such ID.
    *
    * @param id The ID of the service to be located
    */
  case class LocateService(id: ServiceId)

  /** A request to eventually locate a service with the given ID.
    *
    * The service registry will respond with the appropriate [[ServiceLocated]] message if there
    * is a registered service with the given ID. Otherwise, it will wait up to timeout for a
    * service to be registered. If there is a registration while waiting, [[ServiceLocated]] for
    * that service will be sent. Otherwise (the timeout expires), [[ServiceNotFound]] will be sent.
    *
    * @param id       The ID of the service to be located
    * @param timeout  The time the registry must wait for the service to be registered before
    *                 sending a [[ServiceNotFound]] response
    */
  case class EventuallyLocateService(id: ServiceId, timeout: FiniteDuration)

  /** A response indicating the requested service has been located. */
  case class ServiceLocated(id: ServiceId, actor: ActorRef)

  /** A response indicating the requested service has not been found. */
  case class ServiceNotFound(id: ServiceId)

  /** Obtain the properties for a new, fresh service registry actor. */
  def props(): Props = Props(new ServiceRegistryActor)
}
