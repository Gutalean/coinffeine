package coinffeine.protocol.gateway.p2p

import java.net.NetworkInterface
import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging

import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.p2p.P2PNetwork._

object TomP2PNetwork extends P2PNetwork with StrictLogging {

  override def join(id: PeerId,
                    mode: ConnectionMode,
                    acceptedInterfaces: Seq[NetworkInterface],
                    listener: Listener)
                   (implicit ec: ExecutionContext): Future[P2PNetwork.Session] = {
    val peerFactory = mode match {
      case StandaloneNode(broker) => new BrokerPeerFactory(broker)
      case AutodetectPeerNode(port, broker) => new DefaultPeerFactory(broker, port)
      case PortForwardedPeerNode(port, broker) =>
        new DefaultPeerFactory(broker, port, forwardedPort = Some(port))
    }
    for {
      (dht, brokerId) <- peerFactory.build(id, new BindingsBuilder(acceptedInterfaces), listener)
    } yield new TomP2PSession(dht, brokerId)
  }
}
