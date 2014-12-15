package coinffeine.benchmark.config

import java.net.NetworkInterface
import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

import coinffeine.model.network.{NetworkEndpoint, PeerId}

case class CoinffeineProtocolBuilder(protocol: CoinffeineProtocol) {

  def brokerEndpoint(ep: NetworkEndpoint): CoinffeineProtocolBuilder =
    copy(protocol = protocol.copy(brokerEndpoint = ep))

  def brokerEndpoint(hostname: String, port: Int): CoinffeineProtocolBuilder =
    brokerEndpoint(NetworkEndpoint(hostname, port))

  def peerId(id: PeerId) = copy(protocol = protocol.copy(peerId = id))

  def peerPort(port: Int) = copy(protocol = protocol.copy(peerPort = port))

  def ignoredNetworkInterfaces(ifaces: NetworkInterface*) =
    copy(protocol = protocol.copy(ignoredNetworkInterfaces = ifaces))

  def ignoredNetworkInterfaceNames(ifaces: String*)  =
    ignoredNetworkInterfaces(ifaces.map(iface => NetworkInterface.getByName(iface)): _*)

  def connectionRetryInterval(interval: FiniteDuration) =
    copy(protocol = protocol.copy(connectionRetryInterval = interval))

  def externalForwardedPort(port: Int) =
    copy(protocol = protocol.copy(externalForwardedPort = Some(port)))
}

object CoinffeineProtocolBuilder {

  val DefaultCoinffeineProtocolBuilder =
    CoinffeineProtocolBuilder(CoinffeineProtocol.DefaultCoinffeineProtocol)

  implicit def toCoinffeineProtocol(builder: CoinffeineProtocolBuilder): CoinffeineProtocol =
    builder.protocol
}
