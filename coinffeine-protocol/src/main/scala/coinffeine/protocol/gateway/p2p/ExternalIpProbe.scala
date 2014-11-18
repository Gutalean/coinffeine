package coinffeine.protocol.gateway.p2p

import java.net.InetAddress
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._

import net.tomp2p.connection.{ChannelCreator, Bindings}
import net.tomp2p.p2p.PeerMaker
import net.tomp2p.peers.{Number160, PeerAddress}
import org.slf4j.LoggerFactory

import coinffeine.model.network.{NetworkEndpoint, PeerId}

private class ExternalIpProbe(implicit ec: ExecutionContext) {

  def detect(id: PeerId, broker: NetworkEndpoint): Future[InetAddress] = {
    val probePeer = new PeerMaker(Number160Util.fromPeerId(id))
      .setBindings(new Bindings(InetAddress.getLocalHost))
      .makeAndListen()

    def pingBroker(): Future[InetAddress] = {
      for {
        inetSocketAddress <- broker.resolveAsync()
        brokerAddress = new PeerAddress(Number160.ZERO, inetSocketAddress)
        futureResponse <- withChannelCreator(1) { channelCreator =>
          probePeer.getHandshakeRPC.pingTCPDiscover(brokerAddress, channelCreator)
        }
      } yield {
        val seenAs = futureResponse.getResponse.getNeighbors.asScala.head.getInetAddress
        ExternalIpProbe.Log.info("Detected external IP: {}", seenAs)
        seenAs
      }
    }

    def withChannelCreator[T](connections: Int)(block: ChannelCreator => Future[T]): Future[T] = for {
      futureChannelCreator <- probePeer.getConnectionBean.getConnectionReservation.reserve(connections)
      channelCreator = futureChannelCreator.getChannelCreator
      result <- futureFinally(block(channelCreator)) {
        probePeer.getConnectionBean.getConnectionReservation.release(channelCreator, connections)
      }
    } yield result

    futureFinally(pingBroker()) {
      probePeer.shutdown()
    }
  }

  private def futureFinally[T](f: Future[T])(block: => Unit): Future[T] = {
    f.onComplete { _ => block }
    f
  }
}

private object ExternalIpProbe {
  val Log = LoggerFactory.getLogger(classOf[ExternalIpProbe])
}
