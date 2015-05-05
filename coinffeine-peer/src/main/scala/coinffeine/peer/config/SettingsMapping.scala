package coinffeine.peer.config

import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.util.Try

import com.typesafe.config._

import coinffeine.common.TypesafeConfigImplicits
import coinffeine.model.network.PeerId
import coinffeine.overlay.relay.DefaultRelaySettings
import coinffeine.overlay.relay.settings.RelaySettings
import coinffeine.peer.bitcoin.BitcoinSettings
import coinffeine.peer.payment.okpay.OkPaySettings
import coinffeine.protocol.MessageGatewaySettings

trait SettingsMapping[A] {

  /** Read settings from given config. */
  def fromConfig(configPath: File, config: Config): A

  /** Write settings to given config. */
  def toConfig(settings: A, config: Config): Config
}

object SettingsMapping extends TypesafeConfigImplicits {

  val EmptyConfig = ConfigFactory.empty()

  def fromConfig[A](configPath: File, config: Config)(implicit mapping: SettingsMapping[A]): A =
    mapping.fromConfig(configPath, config)

  def toConfig[A](settings: A, config: Config = EmptyConfig)
                 (implicit mapping: SettingsMapping[A]): Config =
    mapping.toConfig(settings, config)

  implicit val general = new SettingsMapping[GeneralSettings] {

    override def fromConfig(configPath: File, config: Config) =
      GeneralSettings(config.getBooleanOpt("coinffeine.licenseAccepted").getOrElse(false))

    override def toConfig(settings: GeneralSettings, config: Config) =
      config.withValue("coinffeine.licenseAccepted", configValue(settings.licenseAccepted))
  }

  implicit val bitcoin = new SettingsMapping[BitcoinSettings] {

    override def fromConfig(configPath: File, config: Config) = {
      val network = BitcoinSettings.parseNetwork(config.getString("coinffeine.bitcoin.network"))
      BitcoinSettings(
        connectionRetryInterval = config.getSeconds("coinffeine.bitcoin.connectionRetryInterval"),
        walletFile = new File(configPath, s"${network.name}.wallet"),
        blockchainFile = new File(configPath, s"${network.name}.h2.db"),
        rebroadcastTimeout = config.getSeconds("coinffeine.bitcoin.rebroadcastTimeout"),
        network = network
      )
    }

    override def toConfig(settings: BitcoinSettings, config: Config) = config
      .withValue("coinffeine.bitcoin.connectionRetryInterval",
        configDuration(settings.connectionRetryInterval))
      .withValue("coinffeine.bitcoin.rebroadcastTimeout",
        configDuration(settings.rebroadcastTimeout))
      .withValue("coinffeine.bitcoin.network", configValue(settings.network.toString))
  }

  implicit object MessageGateway extends SettingsMapping[MessageGatewaySettings] {

    override def fromConfig(configPath: File, config: Config) = MessageGatewaySettings(
      peerId = PeerId(config.getString("coinffeine.peer.id")),
      connectionRetryInterval = config.getSeconds("coinffeine.peer.connectionRetryInterval")
    )

    override def toConfig(settings: MessageGatewaySettings, config: Config) = config
      .withValue("coinffeine.peer.id", configValue(settings.peerId.value))
      .withValue("coinffeine.peer.connectionRetryInterval",
        configDuration(settings.connectionRetryInterval))

    /** Ensure that the given config has a peer ID.
      *
      * If the given config already has a peer id, `None` is returned. Otherwise, a new random
      * peer ID is generated and stored in a copy of the config that is returned as `Some`.
      */
    def ensurePeerId(config: Config): Option[Config] = {
      config.getStringOpt("coinffeine.peer.id") match {
        case Some(_) => None
        case None => Some(
          config.withValue("coinffeine.peer.id", configValue(PeerId.random().value)))
      }
    }
  }

  implicit object Relay extends SettingsMapping[RelaySettings] {

    override def fromConfig(configPath: File, config: Config) = RelaySettings(
      serverAddress = config.getString("coinffeine.overlay.relay.address"),
      serverPort = config.getInt("coinffeine.overlay.relay.port"),
      maxFrameBytes = Try(config.getInt("coinffeine.overlay.relay.maxFrameBytes"))
        .getOrElse(DefaultRelaySettings.MaxFrameBytes),
      identificationTimeout =  Try(
        config.getDuration("coinffeine.overlay.relay.identificationTimeout",
        TimeUnit.SECONDS).seconds).getOrElse(DefaultRelaySettings.IdentificationTimeout),
      minTimeBetweenStatusUpdates =
        config.getSecondsOpt("coinffeine.overlay.relay.server.minTimeBetweenStatusUpdates")
          .getOrElse(DefaultRelaySettings.MinTimeBetweenStatusUpdates),
      connectionTimeout =
        config.getSecondsOpt("coinffeine.overlay.relay.client.connectionTimeout")
          .getOrElse(DefaultRelaySettings.ConnectionTimeout)
    )

    override def toConfig(settings: RelaySettings, config: Config) = config
      .withValue("coinffeine.overlay.relay.address",
        configValue(settings.serverAddress))
      .withValue("coinffeine.overlay.relay.port", configValue(settings.serverPort))
      .withValue("coinffeine.overlay.relay.maxFrameBytes",
        configValue(settings.maxFrameBytes))
      .withValue("coinffeine.overlay.relay.identificationTimeout",
        configValue(s"${settings.identificationTimeout.toSeconds}s"))
      .withValue("coinffeine.overlay.relay.server.minTimeBetweenStatusUpdates",
        configValue(s"${settings.minTimeBetweenStatusUpdates.toSeconds}s"))
      .withValue("coinffeine.overlay.relay.client.connectionTimeout",
        configDuration(settings.connectionTimeout))
  }

  implicit val okPay = new SettingsMapping[OkPaySettings] {

    override def fromConfig(configPath: File, config: Config) = OkPaySettings(
      userAccount = config.getStringOpt("coinffeine.okpay.id"),
      seedToken = config.getStringOpt("coinffeine.okpay.token"),
      serverEndpoint = URI.create(config.getString("coinffeine.okpay.endpoint")),
      pollingInterval = config.getSeconds("coinffeine.okpay.pollingInterval")
    )

    override def toConfig(settings: OkPaySettings, config: Config) = config
      .withValue("coinffeine.okpay.id", configValue(settings.userAccount.getOrElse("")))
      .withValue("coinffeine.okpay.token", configValue(settings.seedToken.getOrElse("")))
      .withValue("coinffeine.okpay.endpoint", configValue(settings.serverEndpoint.toString))
      .withValue("coinffeine.okpay.pollingInterval", configDuration(settings.pollingInterval))
  }

  private def configDuration(duration: FiniteDuration): ConfigValue =
    configValue(s"${duration.toSeconds}s")

  private def configValue[A](value: A): ConfigValue = ConfigValueFactory.fromAnyRef(value)
}
