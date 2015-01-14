package coinffeine.peer.config

import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.util.Try

import com.typesafe.config._

import coinffeine.model.network.{NetworkEndpoint, PeerId}
import coinffeine.overlay.relay.DefaultRelayConfig
import coinffeine.overlay.relay.settings.RelayServerSettings
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

  implicit val general = new SettingsMapping[GeneralSettings] {

    override def fromConfig(config: Config) =
      GeneralSettings(getBoolean(config, "coinffeine.licenseAccepted"))

    override def toConfig(settings: GeneralSettings, config: Config) =
      config.withValue("coinffeine.licenseAccepted", configValue(settings.licenseAccepted))
  }

  implicit val bitcoin = new SettingsMapping[BitcoinSettings] {

    override def fromConfig(config: Config) = BitcoinSettings(
      connectionRetryInterval =
        config.getDuration("coinffeine.bitcoin.connectionRetryInterval", TimeUnit.SECONDS).seconds,
      walletFile = new File(config.getString("coinffeine.bitcoin.walletFile")),
      rebroadcastTimeout =
        config.getDuration("coinffeine.bitcoin.rebroadcastTimeout", TimeUnit.SECONDS).seconds,
      network = BitcoinSettings.parseNetwork(config.getString("coinffeine.bitcoin.network"))
    )

    override def toConfig(settings: BitcoinSettings, config: Config) = config
      .withValue("coinffeine.bitcoin.connectionRetryInterval",
        configValue(s"${settings.connectionRetryInterval.toSeconds}s"))
      .withValue("coinffeine.bitcoin.walletFile", configValue(settings.walletFile.toString))
      .withValue("coinffeine.bitcoin.rebroadcastTimeout",
        configValue(s"${settings.rebroadcastTimeout.toSeconds}s"))
      .withValue("coinffeine.bitcoin.network", configValue(settings.network.toString))
  }

  implicit object MessageGateway extends SettingsMapping[MessageGatewaySettings] {

    override def fromConfig(config: Config) = MessageGatewaySettings(
      peerId = PeerId(config.getString("coinffeine.peer.id")),
      peerPort = config.getInt("coinffeine.peer.port"),
      brokerEndpoint = NetworkEndpoint(
        hostname = config.getString("coinffeine.broker.hostname"),
        port = config.getInt("coinffeine.broker.port")),
      connectionRetryInterval =
        config.getDuration("coinffeine.peer.connectionRetryInterval", TimeUnit.SECONDS).seconds
    )

    override def toConfig(settings: MessageGatewaySettings, config: Config) = config
      .withValue("coinffeine.peer.id", configValue(settings.peerId.value))
      .withValue("coinffeine.peer.port", configValue(settings.peerPort))
      .withValue("coinffeine.broker.hostname", configValue(settings.brokerEndpoint.hostname))
      .withValue("coinffeine.broker.port", configValue(settings.brokerEndpoint.port))
      .withValue("coinffeine.peer.connectionRetryInterval",
        configValue(s"${settings.connectionRetryInterval.toSeconds}s"))

    /** Ensure that the given config has a peer ID.
      *
      * If the given config already has a peer id, `None` is returned. Otherwise, a new random
      * peer ID is generated and stored in a copy of the config that is returned as `Some`.
      */
    def ensurePeerId(config: Config): Option[Config] = {
      getOptionalString(config, "coinffeine.peer.id") match {
        case Some(_) => None
        case None => Some(
          config.withValue("coinffeine.peer.id", configValue(PeerId.random().value)))
      }
    }
  }

  implicit object RelayServer extends SettingsMapping[RelayServerSettings] {

    override def fromConfig(config: Config) = RelayServerSettings(
      bindAddress = config.getString("coinffeine.overlay.relay.server.address"),
      bindPort = config.getInt("coinffeine.overlay.relay.server.port"),
      maxFrameBytes = Try(config.getInt("coinffeine.overlay.relay.server.maxFrameBytes"))
        .toOption.getOrElse(DefaultRelayConfig.MaxFrameBytes),
      identificationTimeout =  Try(
        config.getDuration("coinffeine.overlay.relay.server.identificationTimeout",
        TimeUnit.SECONDS).seconds).toOption.getOrElse(DefaultRelayConfig.IdentificationTimeout),
      minTimeBetweenStatusUpdates = Try(
        config.getDuration("coinffeine.overlay.relay.server.minTimeBetweenStatusUpdates",
          TimeUnit.SECONDS).seconds).toOption.getOrElse(DefaultRelayConfig.MinTimeBetweenStatusUpdates)
    )

    override def toConfig(settings: RelayServerSettings, config: Config) = config
      .withValue("coinffeine.overlay.relay.server.address",
        configValue(settings.bindAddress))
      .withValue("coinffeine.overlay.relay.server.port", configValue(settings.bindPort))
      .withValue("coinffeine.overlay.relay.server.maxFrameBytes",
        configValue(settings.maxFrameBytes))
      .withValue("coinffeine.overlay.relay.server.identificationTimeout",
        configValue(s"${settings.identificationTimeout.toSeconds}s"))
      .withValue("coinffeine.overlay.relay.server.minTimeBetweenStatusUpdates",
        configValue(s"${settings.minTimeBetweenStatusUpdates.toSeconds}s"))
  }

  implicit val okPay = new SettingsMapping[OkPaySettings] {

    override def fromConfig(config: Config) = OkPaySettings(
      userAccount = getOptionalString(config, "coinffeine.okpay.id"),
      seedToken = getOptionalString(config, "coinffeine.okpay.token"),
      serverEndpoint = URI.create(config.getString("coinffeine.okpay.endpoint")),
      pollingInterval =
        config.getDuration("coinffeine.okpay.pollingInterval", TimeUnit.SECONDS).seconds
    )

    override def toConfig(settings: OkPaySettings, config: Config) = config
      .withValue("coinffeine.okpay.id", configValue(settings.userAccount.getOrElse("")))
      .withValue("coinffeine.okpay.token", configValue(settings.seedToken.getOrElse("")))
      .withValue("coinffeine.okpay.endpoint", configValue(settings.serverEndpoint.toString))
      .withValue("coinffeine.okpay.pollingInterval",
        configValue(s"${settings.pollingInterval.toSeconds}s"))
  }

  private def getOptionalString(config: Config, key: String): Option[String] = try {
    val value = config.getString(key).trim
    if (value.isEmpty) None else Some(value)
  } catch {
    case _: ConfigException.Missing => None
  }

  /** Get boolean key with missing-is-false semantics */
  private def getBoolean(config: Config, key: String): Boolean = try {
    config.getBoolean(key)
  } catch {
    case _: ConfigException.Missing => false
  }

  private def configValue[A](value: A): ConfigValue = ConfigValueFactory.fromAnyRef(value)
}
