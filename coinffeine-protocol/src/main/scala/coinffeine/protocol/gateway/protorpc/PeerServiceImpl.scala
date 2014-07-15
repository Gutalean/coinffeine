package coinffeine.protocol.gateway.protorpc

import akka.actor.ActorRef
import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.PeerConnection
import coinffeine.protocol.protobuf.CoinffeineProtobuf.PeerIdResolution
import coinffeine.protocol.protobuf.{CoinffeineProtobuf => proto}
import com.google.protobuf.{RpcCallback, RpcController}
import com.googlecode.protobuf.pro.duplex.execute.ServerRpcController

/** Adapter class that implements the protobuf server interface */
private[gateway] class PeerServiceImpl(ref: ActorRef)
  extends proto.PeerService.Interface {

  import coinffeine.protocol.gateway.protorpc.PeerServiceImpl._

  override def sendMessage(controller: RpcController,
                           message: proto.CoinffeineMessage,
                           done: RpcCallback[proto.Void]): Unit = {
    done.run(VoidResponse)
    ref ! ReceiveProtoMessage(message, clientPeerConnection(controller))
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

  case class ReceiveProtoMessage(message: proto.CoinffeineMessage, senderConnection: PeerConnection)
}
