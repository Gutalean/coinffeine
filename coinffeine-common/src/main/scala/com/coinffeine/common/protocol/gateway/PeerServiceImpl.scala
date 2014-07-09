package com.coinffeine.common.protocol.gateway

import akka.actor.ActorRef
import com.google.protobuf.{RpcCallback, RpcController}
import com.googlecode.protobuf.pro.duplex.execute.ServerRpcController

import com.coinffeine.common.PeerConnection
import com.coinffeine.common.exchange.PeerId
import com.coinffeine.common.protocol.protobuf.{CoinffeineProtobuf => proto}
import com.coinffeine.common.protocol.protobuf.CoinffeineProtobuf.PeerIdResolution

/** Adapter class that implements the protobuf server interface */
private[gateway] class PeerServiceImpl(ref: ActorRef)
  extends proto.PeerService.Interface {

  import PeerServiceImpl._

  override def sendMessage(controller: RpcController,
                           message: proto.CoinffeineMessage,
                           done: RpcCallback[proto.Void]): Unit = {
    done.run(VoidResponse)
    ref ! ReceiveMessage(message, clientPeerConnection(controller))
  }

  private def clientPeerConnection(controller: RpcController) = {
    val info = controller.asInstanceOf[ServerRpcController].getRpcClient.getServerInfo
    PeerConnection(info.getHostName, info.getPort)
  }

  override def resolvePeerId(controller: RpcController, request: proto.PeerId,
                             callback: RpcCallback[PeerIdResolution]): Unit = {
    ref ! ResolvePeerId(PeerId(request.getPeerId), callback)
  }
}

private[gateway] object PeerServiceImpl {

  private[protocol] val VoidResponse = proto.Void.newBuilder().build()

  case class ResolvePeerId(peerId: PeerId, callback: RpcCallback[PeerIdResolution])

  case class ReceiveMessage(message: proto.CoinffeineMessage, senderConnection: PeerConnection)
}
