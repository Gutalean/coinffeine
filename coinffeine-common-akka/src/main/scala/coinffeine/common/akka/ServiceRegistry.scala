package coinffeine.common.akka

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.util.Timeout

/** A convenience regular class to interact with the service registry actor. */
class ServiceRegistry(registry: ActorRef) {
  import ServiceRegistry._
  import ServiceRegistryActor._

  def register(service: ServiceId, actor: ActorRef): Unit = {
    registry.tell(RegisterService(service, actor), ActorRef.noSender)
  }

  def locateFuture(service: ServiceId)
                  (implicit timeout: FiniteDuration = DefaultLocateTimeout,
                   executor: ExecutionContext): Future[ActorRef] =
    locateFutureWith(service, LocateService(service))

  def eventuallyLocateFuture(service: ServiceId)
                            (implicit timeout: FiniteDuration = DefaultLocateTimeout,
                             executor: ExecutionContext): Future[ActorRef] =
    locateFutureWith(service, EventuallyLocateService(service, timeout))

  def locate(service: ServiceId)
            (implicit timeout: FiniteDuration = DefaultLocateTimeout,
             executor: ExecutionContext): ActorRef =
    Await.result(locateFuture(service), timeout)

  def eventuallyLocate(service: ServiceId)
                      (implicit timeout: FiniteDuration = DefaultLocateTimeout,
                       executor: ExecutionContext): ActorRef =
    Await.result(eventuallyLocateFuture(service), timeout)

  private def locateFutureWith[A](service: ServiceId, msg: A)
                                 (implicit timeout: FiniteDuration,
                                  executor: ExecutionContext): Future[ActorRef] = {
    implicit val to = Timeout(timeout)
    AskPattern(registry, msg, s"cannot locate $service: registry is unavailable")
      .withReply[Any]()
      .flatMap {
      case ServiceLocated(`service`, actor) =>
        Future.successful(actor)
      case ServiceNotFound(`service`) =>
        Future.failed(new IllegalArgumentException(s"cannot locate $service: no such service"))
    }
  }
}

object ServiceRegistry {

  val DefaultLocateTimeout = 500.millis
}
