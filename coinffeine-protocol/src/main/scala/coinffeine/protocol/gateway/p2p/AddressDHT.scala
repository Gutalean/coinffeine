package coinffeine.protocol.gateway.p2p

import scala.concurrent.{Future, ExecutionContext}

import com.typesafe.scalalogging.StrictLogging
import net.tomp2p.futures.FutureDHT
import net.tomp2p.p2p.Peer
import net.tomp2p.peers.PeerAddress

import coinffeine.common.ScalaFutureImplicits
import coinffeine.model.network.PeerId

/** Stores/recovers peer addresses from the DHT */
private object AddressDHT
  extends ScalaFutureImplicits with TomP2PFutureImplicits with StrictLogging {

  case class AddressPublicationException(address: PeerAddress, put: FutureDHT)
    extends Exception(s"Cannot publish $address: ${put.getFailedReason}")

  def storeOwnAddress(dht: Peer)(implicit ec: ExecutionContext): Future[Unit] =
    store(dht, dht.getPeerAddress)

  def store(dht: Peer, address: PeerAddress)(implicit ec: ExecutionContext): Future[Unit] = {
    val put = dht.put(address.getID)
      .setObject(address)
      .start()
    logger.info("Publishing address {}", address)
    put.transform(
      result => (),
      error => AddressPublicationException(address, put)
    )
  }

  def removeOwnAddress(dht: Peer)(implicit ec: ExecutionContext): Future[FutureDHT] = {
    logger.info("Unpublishing address for {}", dht.getPeerID)
    dht.remove(dht.getPeerID).start()
  }

  def recover(dht: Peer, peerId: PeerId)
             (implicit ec: ExecutionContext): Future[Option[PeerAddress]] =
    dht.get(Number160Util.fromPeerId(peerId)).start()
      .map(_.getData.getObject.asInstanceOf[PeerAddress])
      .materialize
      .map(_.toOption)
}
