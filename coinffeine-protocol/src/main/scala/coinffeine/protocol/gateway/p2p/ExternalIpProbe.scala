package coinffeine.protocol.gateway.p2p

import java.net.InetAddress
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import net.tomp2p.connection.{Bindings, ChannelCreator}
import net.tomp2p.p2p.PeerMaker
import net.tomp2p.peers.{Number160, PeerAddress}

import coinffeine.model.network.{NetworkEndpoint, PeerId}

private class ExternalIpProbe(implicit ec: ExecutionContext) extends StrictLogging {

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
        logger.info("Detected external IP: {}", seenAs)
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
