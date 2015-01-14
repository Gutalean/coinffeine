package coinffeine.benchmark.config

import java.net.NetworkInterface
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.reflect.runtime.universe._

import akka.actor._
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import io.gatling.core.config.Protocol

import coinffeine.common.akka.{AskPattern, ServiceActor}
import coinffeine.common.test.DefaultTcpPortAllocator
import coinffeine.model.network.{NetworkEndpoint, NodeId, PeerId}
import coinffeine.peer.api.impl.ProductionCoinffeineComponent
import coinffeine.protocol.MessageGatewaySettings
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.messages.PublicMessage

case class CoinffeineProtocol(
    brokerEndpoint: NetworkEndpoint,
    peerId: PeerId,
    peerPort: Int,
    ignoredNetworkInterfaces: Seq[NetworkInterface],
    connectionRetryInterval: FiniteDuration,
    externalForwardedPort: Option[Int]) extends Protocol with StrictLogging {

  private val gatewaySettings = MessageGatewaySettings(
    peerId, peerPort, brokerEndpoint, ignoredNetworkInterfaces,
    connectionRetryInterval, externalForwardedPort)

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
      actorSystem.get.actorOf(GatewayComponent.messageGatewayProps(gatewaySettings)(actorSystem.get)))

    val startCommand = ServiceActor.Start(Join(gatewaySettings))
    val gatewayStart = AskPattern(gatewayActor.get, startCommand)
      .withReply[ServiceActor.Started.type]
    Await.result(gatewayStart, 20.seconds)

    // This is not the code fragment I'm most proud of
    while (!GatewayComponent.coinffeineNetworkProperties.isConnected) {
      logger.info("Waiting for Coinffeine message gateway to be connected... ")
      Thread.sleep(1000)
    }
  }

  private object GatewayComponent extends ProductionCoinffeineComponent

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
    peerPort = DefaultTcpPortAllocator.allocatePort(),
    ignoredNetworkInterfaces = Seq.empty,
    connectionRetryInterval = 30.seconds,
    externalForwardedPort = None)
}
