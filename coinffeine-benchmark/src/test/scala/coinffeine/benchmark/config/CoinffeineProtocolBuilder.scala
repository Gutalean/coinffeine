package coinffeine.benchmark.config

import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

import coinffeine.model.network.{NetworkEndpoint, PeerId}

case class CoinffeineProtocolBuilder(protocol: CoinffeineProtocol) {

  def brokerEndpoint(ep: NetworkEndpoint): CoinffeineProtocolBuilder =
    copy(protocol = protocol.copy(brokerEndpoint = ep))

  def brokerEndpoint(hostname: String, port: Int): CoinffeineProtocolBuilder =
    brokerEndpoint(NetworkEndpoint(hostname, port))

  def peerId(id: PeerId) = copy(protocol = protocol.copy(peerId = id))

  def connectionRetryInterval(interval: FiniteDuration) =
    copy(protocol = protocol.copy(connectionRetryInterval = interval))
}

object CoinffeineProtocolBuilder {

  val DefaultCoinffeineProtocolBuilder =
    CoinffeineProtocolBuilder(CoinffeineProtocol.DefaultCoinffeineProtocol)

  implicit def toCoinffeineProtocol(builder: CoinffeineProtocolBuilder): CoinffeineProtocol =
    builder.protocol
}
