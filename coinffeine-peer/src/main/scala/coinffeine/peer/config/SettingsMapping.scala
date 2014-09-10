package coinffeine.peer.config

import java.net.{URI, NetworkInterface}
import java.util.concurrent.TimeUnit
import scala.collection.JavaConversions._
import scala.concurrent.duration._

import com.typesafe.config._

import coinffeine.peer.bitcoin.BitcoinSettings
import coinffeine.peer.payment.okpay.OkPaySettings
import coinffeine.protocol.MessageGatewaySettings

trait SettingsMapping[A] {

  /** Read settings from given config. */
  def fromConfig(config: Config): A

  /** Write settings to given config. */
  def toConfig(settings: A, config: Config): Config
}

object SettingsMapping {

  val EmptyConfig = ConfigFactory.empty()

  def fromConfig[A](config: Config)
                   (implicit mapping: SettingsMapping[A]): A = mapping.fromConfig(config)

  def toConfig[A](settings: A, config: Config = EmptyConfig)
                 (implicit mapping: SettingsMapping[A]): Config = mapping.toConfig(settings, config)

  implicit val bitcoin = new SettingsMapping[BitcoinSettings] {

    override def fromConfig(config: Config) = BitcoinSettings(
      connectionRetryInterval =
        config.getDuration("coinffeine.bitcoin.connectionRetryInterval", TimeUnit.SECONDS).seconds
    )

    override def toConfig(settings: BitcoinSettings, config: Config) = config
      .withValue("coinffeine.bitcoin.connectionRetryInterval",
        configValue(s"${settings.connectionRetryInterval.toSeconds}s"))
  }

  implicit val messageGateway = new SettingsMapping[MessageGatewaySettings] {

    override def fromConfig(config: Config) = MessageGatewaySettings(
      peerPort = config.getInt("coinffeine.peer.port"),
      brokerHost = config.getString("coinffeine.broker.hostname"),
      brokerPort = config.getInt("coinffeine.broker.port"),
      ignoredNetworkInterfaces(config)
    )

    /** Write settings to given config. */
    override def toConfig(settings: MessageGatewaySettings, config: Config) = config
      .withValue("coinffeine.peer.port", configValue(settings.peerPort))
      .withValue("coinffeine.broker.hostname", configValue(settings.brokerHost))
      .withValue("coinffeine.broker.port", configValue(settings.brokerPort))
      .withValue("coinffeine.peer.ifaces.ignore",
        configValue(asJavaIterable(settings.ignoredNetworkInterfaces.map(_.getName))))

    private def ignoredNetworkInterfaces(config: Config): Seq[NetworkInterface] = try {
      config.getStringList("coinffeine.peer.ifaces.ignore")
        .flatMap(name => Option(NetworkInterface.getByName(name)))
    } catch {
      case _: ConfigException.Missing => Seq.empty
    }
  }

  implicit val okPay = new SettingsMapping[OkPaySettings] {

    override def fromConfig(config: Config) = OkPaySettings(
      userAccount = config.getString("coinffeine.okpay.id"),
      seedToken = config.getString("coinffeine.okpay.token"),
      serverEndpoint = URI.create(config.getString("coinffeine.okpay.endpoint")),
      pollingInterval =
        config.getDuration("coinffeine.okpay.pollingInterval", TimeUnit.SECONDS).seconds
    )

    /** Write settings to given config. */
    override def toConfig(settings: OkPaySettings, config: Config) = config
      .withValue("coinffeine.okpay.id", configValue(settings.userAccount))
      .withValue("coinffeine.okpay.token", configValue(settings.seedToken))
      .withValue("coinffeine.okpay.endpoint", configValue(settings.serverEndpoint.toString))
      .withValue("coinffeine.okpay.pollingInterval",
        configValue(s"${settings.pollingInterval.toSeconds}s"))
  }

  private def configValue[A](value: A): ConfigValue = ConfigValueFactory.fromAnyRef(value)
}
