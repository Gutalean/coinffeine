package coinffeine.protocol

import java.net.NetworkInterface
import scala.collection.JavaConversions._

import com.typesafe.config.{ConfigException, Config}

case class MessageGatewaySettings(
  peerPort: Int,
  brokerHost: String,
  brokerPort: Int,
  ignoredNetworkInterfaces: Seq[NetworkInterface]
)

object MessageGatewaySettings {

  def apply(config: Config): MessageGatewaySettings = MessageGatewaySettings(
    peerPort = config.getInt("coinffeine.peer.port"),
    brokerHost = config.getString("coinffeine.broker.hostname"),
    brokerPort = config.getInt("coinffeine.broker.port"),
    ignoredNetworkInterfaces(config)
  )

  private def ignoredNetworkInterfaces(config: Config): Seq[NetworkInterface] = try {
    config.getStringList("coinffeine.peer.ifaces.ignore")
      .flatMap(name => Option(NetworkInterface.getByName(name)))
  } catch {
    case _: ConfigException.Missing => Seq.empty
  }
}
