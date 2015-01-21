package coinffeine.benchmark.config

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}

import akka.actor._
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import io.gatling.core.config.Protocol

import coinffeine.common.akka.ServiceActor
import coinffeine.model.network.{NetworkEndpoint, NodeId, PeerId}
import coinffeine.overlay.relay.settings.RelayClientSettings
import coinffeine.peer.api.impl.ProductionCoinffeineComponent
import coinffeine.protocol.MessageGatewaySettings
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.messages.PublicMessage

case class CoinffeineProtocol(
    brokerEndpoint: NetworkEndpoint,
    peerId: PeerId,
    connectionRetryInterval: FiniteDuration) extends Protocol with StrictLogging {

  private val gatewaySettings = MessageGatewaySettings(peerId, connectionRetryInterval)
  private val relaySettings = RelayClientSettings(brokerEndpoint.hostname, brokerEndpoint.port)

  private var actorSystem: Option[ActorSystem] = None
  private var gatewayActor: Option[ActorRef] = None

  def ask(msg: PublicMessage, dest: NodeId, responseMatcher: PartialFunction[PublicMessage, Unit])
         (implicit to: Timeout): Future[PublicMessage] = {
    require(actorSystem.isDefined)
    require(gatewayActor.isDefined)

    val promise = Promise[PublicMessage]()
    actorSystem.get.actorOf(Props(new ResponseReceiver(msg, dest, responseMatcher, promise)))
    promise.future
  }

  override def warmUp() = {
    initMessageGateway()
  }

  private def initMessageGateway(): Unit = {
    implicit val timeout = Timeout(20.seconds)

    actorSystem = Some(ActorSystem("coinffeine-benchmark"))
    gatewayActor = Some(
      actorSystem.get.actorOf(GatewayComponent.messageGatewayProps(gatewaySettings, relaySettings)(actorSystem.get)))

    Await.result(ServiceActor.askStart(gatewayActor.get), 20.seconds)

    // This is not the code fragment I'm most proud of
    while (!GatewayComponent.coinffeineNetworkProperties.isConnected) {
      logger.info("Waiting for Coinffeine message gateway to be connected... ")
      Thread.sleep(1000)
    }
  }

  private object GatewayComponent extends ProductionCoinffeineComponent {
    override def commandLineArgs = List.empty
  }

  private class ResponseReceiver(request: PublicMessage,
                                 destination: NodeId,
                                 responseMatcher: PartialFunction[PublicMessage, Unit],
                                 promise: Promise[PublicMessage])
                                (implicit to: Timeout) extends Actor {

    require(gatewayActor.isDefined)

    gatewayActor.get ! Subscribe {
      case ReceiveMessage(msg, `destination`) if responseMatcher.isDefinedAt(msg) =>
    }
    gatewayActor.get ! ForwardMessage(request, destination)
    this.context.setReceiveTimeout(to.duration)

    override def receive = {
      case ReceiveMessage(m, _) =>
        promise.success(m)
        self ! PoisonPill
      case ReceiveTimeout =>
        promise.failure(new RuntimeException(s"timeout while expecting message response"))
        self ! PoisonPill
    }
  }
}

object CoinffeineProtocol {

  val DefaultCoinffeineProtocol = CoinffeineProtocol(
    brokerEndpoint = NetworkEndpoint("dev.coinffeine.pri", 9009),
    peerId = PeerId.random(),
    connectionRetryInterval = 30.seconds)
}
