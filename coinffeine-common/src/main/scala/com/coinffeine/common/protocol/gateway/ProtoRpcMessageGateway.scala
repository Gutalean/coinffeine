package com.coinffeine.common.protocol.gateway

import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.actor._
import akka.pattern._
import akka.util.Timeout

import com.coinffeine.common.PeerConnection
import com.coinffeine.common.exchange.PeerId
import com.coinffeine.common.network.NetworkComponent
import com.coinffeine.common.protocol.gateway.MessageGateway._
import com.coinffeine.common.protocol.gateway.PeerIdResolverActor.{AddMapping, LookupMapping}
import com.coinffeine.common.protocol.gateway.PeerServiceImpl.ReceiveProtoMessage
import com.coinffeine.common.protocol.gateway.ProtobufServerActor.PeerWith
import com.coinffeine.common.protocol.messages.PublicMessage
import com.coinffeine.common.protocol.protobuf.{CoinffeineProtobuf => proto}
import com.coinffeine.common.protocol.serialization.{ProtocolSerialization, ProtocolSerializationComponent}
import com.coinffeine.common.protorpc.{Callbacks, PeerSession}

private class ProtoRpcMessageGateway(serialization: ProtocolSerialization)
  extends Actor with ActorLogging {

  import context.dispatcher
  implicit private val timeout = Timeout(10.seconds)

  private val subscriptions = context.actorOf(SubscriptionManagerActor.props, "subscriptions")
  private val resolver = context.actorOf(PeerIdResolverActor.props, "resolver")
  private val server = context.actorOf(ProtobufServerActor.props, "server")

  override def receive = waitingForInitialization orElse managingSubscriptions

  private val managingSubscriptions: Receive = {
    case msg: Subscribe =>
      context.watch(sender())
      subscriptions forward msg
    case msg @ Unsubscribe => subscriptions forward msg
    case Terminated(actor) => subscriptions.tell(Unsubscribe, actor)
  }

  private val waitingForInitialization: Receive = {
    case bind @ Bind(ownId, peerConnection, brokerId, brokerConnection) =>
      server ! bind
      context.become(binding(ownId, brokerId, brokerConnection, sender())
        orElse managingSubscriptions)
  }

  private def binding(ownId: PeerId, brokerId: PeerId, brokerConnection: PeerConnection,
                      listener: ActorRef): Receive = {

    case boundTo @ BoundTo(ownConnection) =>
      listener ! boundTo
      new StartedGateway(ownId, ownConnection, brokerId, brokerConnection).start()

    case bindingError: BindingError =>
      log.info(s"Message gateway $ownId couldn't start")
      listener ! bindingError
      context.become(receive)
  }

  private class StartedGateway(ownId: PeerId, ownConnection: PeerConnection,
                               brokerId: PeerId, brokerConnection: PeerConnection) {

    def start(): Unit = {
      context.become(forwardingMessages orElse managingSubscriptions)
      resolver ! PeerIdResolverActor.Start(brokerId, brokerConnection, server)
      log.info(s"Message gateway started on $ownConnection with id $ownId")
    }

    private val forwardingMessages: Receive = {
      case m @ ForwardMessage(msg, destId) =>
        log.debug(s"Forwarding message $msg to $destId")
        forward(destId, msg)

      case ReceiveProtoMessage(protoMessage, senderConnection) =>
        val (message, senderId) = serialization.fromProtobuf(protoMessage)
        resolver ! AddMapping(senderId, senderConnection)
        subscriptions ! ReceiveMessage(message, senderId)
    }

    private def forward(to: PeerId, message: PublicMessage): Unit = {
      (resolver ? LookupMapping(to)).mapTo[PeerConnection].onComplete {
        case Success(connection) => forward(connection, message)
        case Failure(cause) =>
          log.error(s"Cannot resolve peer with id $to, dropping $message")
      }
    }

    private def forward(to: PeerConnection, message: PublicMessage): Unit = {
      (server ? PeerWith(to)).mapTo[PeerSession].map { s =>
        proto.PeerService.newStub(s.channel).sendMessage(
          s.controller,
          serialization.toProtobuf(message, ownId),
          Callbacks.noop[proto.Void]
        )
      }
    }
  }
}

object ProtoRpcMessageGateway {

  trait Component extends MessageGateway.Component {
    this: ProtocolSerializationComponent with NetworkComponent=>

    override lazy val messageGatewayProps = Props(new ProtoRpcMessageGateway(protocolSerialization))
  }
}
