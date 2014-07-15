package com.coinffeine.common.protocol.gateway

import java.io.IOException

import coinffeine.model.network.PeerId

import scala.concurrent.duration._
import scala.util.{Try, Failure, Success}

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import com.google.protobuf.RpcCallback

import com.coinffeine.common.PeerConnection
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
      try {
        proto.PeerService.newStub(session.channel).resolvePeerId(
          session.controller, proto.PeerId.newBuilder.setPeerId(peerId.value).build(),
          new ExternalLookupCallback(peerId, requester))
      } catch {
        case e: IOException => throw ForwardException(s"cannot ask for $peerId resolution", e)
      }
    }

    private class ExternalLookupCallback(peerId: PeerId, requester: ActorRef)
      extends RpcCallback[proto.PeerIdResolution] {

      override def run(resolution: proto.PeerIdResolution): Unit = {
        requireValidConnection(resolution) match {
          case Success(connection) =>
            self ! AddMapping(peerId, connection)
            requester ! connection
          case Failure(cause) =>
            requester ! Status.Failure(cause)
        }
      }

      private def requireValidConnection(resolution: proto.PeerIdResolution): Try[PeerConnection] =
        for {
          rawConnection <- requireExistingConnection(resolution)
          parsedConnection <- Try(PeerConnection.parse(rawConnection))
        } yield parsedConnection

      private def requireExistingConnection(resolution: proto.PeerIdResolution): Try[String] =
        if (resolution.hasPeerConnection) Success(resolution.getPeerConnection)
        else Failure(new ForwardException(s"$peerId is unknown to the broker"))
    }
  }
}

private[gateway] object PeerIdResolverActor {
  val props: Props = Props(new PeerIdResolverActor())

  case class Start(brokerId: PeerId, brokerConnection: PeerConnection, sessionManager: ActorRef)

  /** Enriches the resolver with a new mapping. */
  case class AddMapping(peerId: PeerId, peerConnection: PeerConnection)

  /** Asks for the PeerConnection of a peer. Either a PeerConnection or an Status.Failure
    * will be sent back.
    */
  case class LookupMapping(peerId: PeerId)
}
