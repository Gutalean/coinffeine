package com.coinffeine.common.protocol.gateway

import java.io.IOException
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import com.google.protobuf.RpcCallback

import com.coinffeine.common.PeerConnection
import com.coinffeine.common.exchange.PeerId
import com.coinffeine.common.protocol.gateway.MessageGateway.ForwardException
import com.coinffeine.common.protocol.gateway.PeerServiceImpl.ResolvePeerId
import com.coinffeine.common.protocol.gateway.ProtobufServerActor.PeerWith
import com.coinffeine.common.protocol.protobuf.{CoinffeineProtobuf => proto}
import com.coinffeine.common.protocol.protobuf.CoinffeineProtobuf.PeerIdResolution
import com.coinffeine.common.protorpc.PeerSession

private class PeerIdResolverActor extends Actor with ActorLogging {
  import PeerIdResolverActor._
  import context.dispatcher

  private var peerMap = Map.empty[PeerId, PeerConnection]

  override def receive: Receive = waitForInitialization orElse updatingPeerMap

  private val waitForInitialization: Receive = {
    case initMessage: Start => new InitializedResolver(initMessage).start()
  }
  private val updatingPeerMap: Receive = {
    case AddMapping(id, connection) => peerMap += id -> connection
  }

  private class InitializedResolver(initMessage: Start) {
    import initMessage._

    private implicit val sessionTimeout = Timeout(3.seconds)

    def start(): Unit = {
      peerMap += brokerId -> brokerConnection
      context.become(resolving orElse updatingPeerMap)
    }

    private val resolving: Receive = {
      case ResolvePeerId(id, callback) => resolveCallback(peerMap.get(id), callback)
      case LookupMapping(peerId) => lookup(peerId, sender())
    }
    /** Resolve a peer id resolution.
      *
      * @param connectionOpt  Associated PeerConnection or None if unknown
      * @param callback       Callback to invoke
      */
    private def resolveCallback(connectionOpt: Option[PeerConnection],
                                callback: RpcCallback[PeerIdResolution]): Unit = {
      val response = PeerIdResolution.newBuilder
      connectionOpt.foreach { c => response.setPeerConnection(c.toString)}
      callback.run(response.build())
    }

    private def lookup(peerId: PeerId, requester: ActorRef) = peerMap.get(peerId) match {
      case Some(connection) => requester ! connection
      case None =>
        (sessionManager ? PeerWith(brokerConnection)).mapTo[PeerSession].onComplete {
          case Success(session) => externalLookup(session, peerId, requester)
          case Failure(cause) => requester ! Status.Failure(cause)
        }
    }

    private def externalLookup(session: PeerSession, peerId: PeerId, requester: ActorRef): Unit = {

      log.info(s"Asking the broker to resolve $peerId")
      val callback = new RpcCallback[proto.PeerIdResolution] {
        override def run(resolution: proto.PeerIdResolution): Unit = {
          if (resolution.hasPeerConnection) {
            val connection = PeerConnection.parse(resolution.getPeerConnection)
            self ! AddMapping(peerId, connection)
            requester ! connection
          } else {
            log.error(s"$peerId is unknown to the broker")
            requester ! Status.Failure(new ForwardException(s"$peerId is unknown to the broker"))
          }
        }
      }

      try {
        proto.PeerService.newStub(session.channel).resolvePeerId(
          session.controller, proto.PeerId.newBuilder.setPeerId(peerId.value).build(), callback)
      } catch {
        case e: IOException => throw ForwardException(s"cannot ask for $peerId resolution", e)
      }
    }
  }
}

private[gateway] object PeerIdResolverActor {
  val props: Props = Props(new PeerIdResolverActor())

  case class Start(brokerId: PeerId, brokerConnection: PeerConnection, sessionManager: ActorRef)

  case class AddMapping(peerId: PeerId, peerConnection: PeerConnection)

  case class LookupMapping(peerId: PeerId)
}
