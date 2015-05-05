package coinffeine.peer.config

import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.util.Try

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatest.OptionValues

import coinffeine.common.test.UnitTest
import coinffeine.model.network.PeerId
import coinffeine.overlay.relay.DefaultRelaySettings
import coinffeine.overlay.relay.settings.RelaySettings
import coinffeine.peer.bitcoin.BitcoinSettings
import coinffeine.peer.payment.okpay.OkPaySettings
import coinffeine.protocol.MessageGatewaySettings

class SettingsMappingTest extends UnitTest with OptionValues {

  val basePath = new File("/home/foo/.coinffeine")
  def fromConfig[T: SettingsMapping](config: Config): T =
    SettingsMapping.fromConfig[T](basePath, config)

  "General settings mapping" should "map from config" in {
    val baseConfig = makeConfig("coinffeine.serviceStartStopTimeout" -> "30s")
    fromConfig[GeneralSettings](baseConfig).licenseAccepted shouldBe false
    fromConfig[GeneralSettings](baseConfig).serviceStartStopTimeout shouldBe 30.seconds
    fromConfig[GeneralSettings](amendConfig(baseConfig, "coinffeine.licenseAccepted" -> false))
      .licenseAccepted shouldBe false
    fromConfig[GeneralSettings](amendConfig(baseConfig, "coinffeine.licenseAccepted" -> true))
      .licenseAccepted shouldBe true
  }

  it should "map to config" in {
    val settings = GeneralSettings(
      licenseAccepted = false,
      serviceStartStopTimeout = 30.seconds
    )
    val cfg = SettingsMapping.toConfig(settings)
    cfg.getBoolean("coinffeine.licenseAccepted") shouldBe false
    cfg.getDuration("coinffeine.serviceStartStopTimeout", TimeUnit.SECONDS) shouldBe 30
  }

  "Bitcoins settings mapping" should "map from config" in {
    val conf = makeConfig(
      "coinffeine.bitcoin.connectionRetryInterval" -> "30s",
      "coinffeine.bitcoin.rebroadcastTimeout" -> "60s",
      "coinffeine.bitcoin.network" -> "mainnet"
    )
    fromConfig[BitcoinSettings](conf) shouldBe BitcoinSettings(
      connectionRetryInterval = 30.seconds,
      walletFile = new File(basePath, "mainnet.wallet"),
      blockchainFile = new File(basePath, "mainnet.h2.db"),
      rebroadcastTimeout = 1.minute,
      network = BitcoinSettings.MainNet
    )
  }

  it should "map to config" in {
    val settings = BitcoinSettings(
      connectionRetryInterval = 50.seconds,
      walletFile = new File("/tmp/user.wallet"),
      blockchainFile = new File("/tmp/foo"),
      rebroadcastTimeout = 60.seconds,
      network = BitcoinSettings.PublicTestnet
    )
    val cfg = SettingsMapping.toConfig(settings)
    cfg.getDuration("coinffeine.bitcoin.connectionRetryInterval", TimeUnit.SECONDS) shouldBe 50
    cfg.getDuration("coinffeine.bitcoin.rebroadcastTimeout", TimeUnit.SECONDS) shouldBe 60
    cfg.getString("coinffeine.bitcoin.network") shouldBe "public-testnet"
  }

  val messageGatewayBasicSettings = makeConfig(
    "coinffeine.peer.id" -> "1234",
    "coinffeine.peer.connectionRetryInterval" -> "10s"
  )

  "Message Gateway settings mapping" should "map basic settings from config" in {
    val settings = fromConfig[MessageGatewaySettings](messageGatewayBasicSettings)
    settings.peerId shouldBe PeerId("1234")
    settings.connectionRetryInterval shouldBe 10.seconds
  }

  it should "map to config" in {
    val settings = MessageGatewaySettings(
      peerId = PeerId("1234"),
      connectionRetryInterval = 10.seconds
    )
    val cfg = SettingsMapping.toConfig(settings)
    cfg.getString("coinffeine.peer.id") shouldBe "1234"
    cfg.getDuration("coinffeine.peer.connectionRetryInterval", TimeUnit.SECONDS) shouldBe 10
  }

  it should "ensure peer ID" in {
    val settings = amendConfig(messageGatewayBasicSettings, "coinffeine.peer.id" -> null)
    val cfg = SettingsMapping.MessageGateway.ensurePeerId(settings)
    cfg shouldBe 'defined
    Try(PeerId(cfg.get.getString("coinffeine.peer.id"))) shouldBe 'success
  }

  val relayServerBasicSettings = makeConfig(
    "coinffeine.overlay.relay.address" -> "localhost",
    "coinffeine.overlay.relay.port" -> 5000
  )

  "Relay server settings mapping" should "map basic settings from config" in {
    val settings = fromConfig[RelaySettings](relayServerBasicSettings)
    settings.serverAddress shouldBe "localhost"
    settings.serverPort shouldBe 5000
    settings.maxFrameBytes shouldBe DefaultRelaySettings.MaxFrameBytes
    settings.identificationTimeout shouldBe DefaultRelaySettings.IdentificationTimeout
    settings.minTimeBetweenStatusUpdates shouldBe DefaultRelaySettings.MinTimeBetweenStatusUpdates
    settings.connectionTimeout shouldBe DefaultRelaySettings.ConnectionTimeout
  }

  it should "map optional settings from config" in {
    val conf = amendConfig(relayServerBasicSettings,
      "coinffeine.overlay.relay.maxFrameBytes" -> "1024",
      "coinffeine.overlay.relay.identificationTimeout" -> "10s",
      "coinffeine.overlay.relay.server.minTimeBetweenStatusUpdates" -> "10m",
      "coinffeine.overlay.relay.client.connectionTimeout" -> "10s"
    )
    val settings = fromConfig[RelaySettings](conf)
    settings.maxFrameBytes shouldBe 1024
    settings.identificationTimeout shouldBe 10.seconds
    settings.minTimeBetweenStatusUpdates shouldBe 10.minutes
    settings.connectionTimeout shouldBe 10.seconds
  }

  it should "map to config" in {
    val settings = RelaySettings(
      serverAddress = "localhost",
      serverPort = 5000,
      maxFrameBytes = 1024,
      identificationTimeout = 10.seconds,
      minTimeBetweenStatusUpdates = 10.minutes
    )
    val cfg = SettingsMapping.toConfig(settings)
    cfg.getString("coinffeine.overlay.relay.address") shouldBe "localhost"
    cfg.getInt("coinffeine.overlay.relay.port") shouldBe 5000
    cfg.getInt("coinffeine.overlay.relay.maxFrameBytes") shouldBe 1024
    cfg.getDuration("coinffeine.overlay.relay.identificationTimeout",
      TimeUnit.SECONDS) shouldBe 10
    cfg.getDuration("coinffeine.overlay.relay.server.minTimeBetweenStatusUpdates",
      TimeUnit.MINUTES) shouldBe 10
  }

  "OKPay settings mapping" should "map from config" in {
    val conf = makeConfig(
      "coinffeine.okpay.id" -> "id",
      "coinffeine.okpay.token" -> "token",
      "coinffeine.okpay.endpoint" -> "http://example.com/death-star",
      "coinffeine.okpay.pollingInterval" -> "50s"
    )
    val settings = fromConfig[OkPaySettings](conf)
    settings.userAccount shouldBe Some("id")
    settings.seedToken shouldBe Some("token")
    settings.serverEndpoint shouldBe new URI("http://example.com/death-star")
    settings.pollingInterval shouldBe 50.seconds

    val settings2 = fromConfig[OkPaySettings](makeConfig(
      "coinffeine.okpay.endpoint" -> "http://example.com/death-star",
      "coinffeine.okpay.pollingInterval" -> "50s"
    ))
    settings2.userAccount shouldBe 'empty
    settings2.seedToken shouldBe 'empty
  }

  it should "map to config" in {
    val settings = OkPaySettings(
      userAccount = Some("skywalker"),
      seedToken = Some("lightsaber"),
      serverEndpoint = new URI("http://example.com/x-wing"),
      pollingInterval = 15.seconds
    )
    val cfg = SettingsMapping.toConfig(settings)
    cfg.getString("coinffeine.okpay.id") shouldBe "skywalker"
    cfg.getString("coinffeine.okpay.token") shouldBe "lightsaber"
    cfg.getString("coinffeine.okpay.endpoint") shouldBe "http://example.com/x-wing"
    cfg.getDuration("coinffeine.okpay.pollingInterval", TimeUnit.SECONDS) shouldBe 15

    val cfg2 = SettingsMapping.toConfig(settings.copy(userAccount = None, seedToken = None))
    cfg2.getString("coinffeine.okpay.id") shouldBe 'empty
    cfg2.getString("coinffeine.okpay.token") shouldBe 'empty
  }

  private def makeConfig(items: (String, Any)*): Config =
    amendConfig(ConfigFactory.empty(), items: _*)

  private def amendConfig(prevConfig: Config, items: (String, Any)*): Config =
    items.foldLeft(prevConfig) {
      case (config, (configPath, configValue)) =>
        config.withValue(configPath, ConfigValueFactory.fromAnyRef(configValue))
    }
}
