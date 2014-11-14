package coinffeine.peer.config

import java.io.File
import java.net.{NetworkInterface, URI}
import java.util.concurrent.TimeUnit
import scala.collection.JavaConversions._
import scala.concurrent.duration._

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatest.OptionValues

import coinffeine.common.test.UnitTest
import coinffeine.model.network.{NetworkEndpoint, PeerId}
import coinffeine.peer.bitcoin.BitcoinSettings
import coinffeine.peer.payment.okpay.OkPaySettings
import coinffeine.protocol.MessageGatewaySettings

class SettingsMappingTest extends UnitTest with OptionValues {

  "Bitcoins settings mapping" should "map from config" in {
    val conf = makeConfig(
      "coinffeine.bitcoin.connectionRetryInterval" -> "30s",
      "coinffeine.bitcoin.walletFile" -> "user.wallet",
      "coinffeine.bitcoin.rebroadcastTimeout" -> "60s",
      "coinffeine.bitcoin.network" -> "mainnet"
    )
    SettingsMapping.fromConfig[BitcoinSettings](conf) shouldBe BitcoinSettings(
      connectionRetryInterval = 30.seconds,
      walletFile = new File("user.wallet"),
      rebroadcastTimeout = 1.minute,
      network = BitcoinSettings.MainNet
    )
  }

  it should "map to config" in {
    val settings = BitcoinSettings(
      connectionRetryInterval = 50.seconds,
      walletFile = new File("/tmp/user.wallet"),
      rebroadcastTimeout = 60.seconds,
      network = BitcoinSettings.PublicTestnet
    )
    val cfg = SettingsMapping.toConfig(settings)
    cfg.getDuration("coinffeine.bitcoin.connectionRetryInterval", TimeUnit.SECONDS) shouldBe 50
    cfg.getString("coinffeine.bitcoin.walletFile") shouldBe new File("/tmp/user.wallet").getPath
    cfg.getDuration("coinffeine.bitcoin.rebroadcastTimeout", TimeUnit.SECONDS) shouldBe 60
    cfg.getString("coinffeine.bitcoin.network") shouldBe "public-testnet"
  }

  val messageGatewayBasicSettings = makeConfig(
    "coinffeine.peer.port" -> 5000,
    "coinffeine.broker.hostname" -> "broker-host",
    "coinffeine.broker.port" -> 4000,
    "coinffeine.peer.ifaces.ignore" -> asJavaIterable(Seq(existingNetInterface().getName)),
    "coinffeine.peer.connectionRetryInterval" -> "10s"
  )

  "Message Gateway settings mapping" should "map basic settings from config" in {
    val settings = SettingsMapping.fromConfig[MessageGatewaySettings](messageGatewayBasicSettings)
    settings.peerId shouldBe 'empty
    settings.peerPort shouldBe 5000
    settings.brokerEndpoint shouldBe NetworkEndpoint("broker-host", 4000)
    settings.ignoredNetworkInterfaces shouldBe Seq(existingNetInterface())
    settings.connectionRetryInterval shouldBe 10.seconds
    settings.externalEndpoint shouldBe 'empty
  }

  it should "map peer ID from config when present" in {
    val conf = amendConfig(messageGatewayBasicSettings, "coinffeine.peer.id" -> "1234")
    SettingsMapping.fromConfig[MessageGatewaySettings](conf).peerId.value shouldBe PeerId("1234")
  }

  it should "map external endpoint from config when present" in {
    val conf = amendConfig(messageGatewayBasicSettings,
      "coinffeine.peer.externalHostname" -> "myexternal",
      "coinffeine.peer.externalPort" -> "8765")
    val settings = SettingsMapping.fromConfig[MessageGatewaySettings](conf)
    settings.externalEndpoint.value shouldBe NetworkEndpoint("myexternal", 8765)
  }

  it should "not map external endpoint from config when hostname is empty" in {
    val conf = amendConfig(messageGatewayBasicSettings,
      "coinffeine.peer.externalHostname" -> "",
      "coinffeine.peer.externalPort" -> "8765")
    val settings = SettingsMapping.fromConfig[MessageGatewaySettings](conf)
    settings.externalEndpoint shouldBe 'empty
  }

  it should "map to config" in {
    val settings = MessageGatewaySettings(
      peerId = Some(PeerId("1234")),
      peerPort = 5050,
      brokerEndpoint = NetworkEndpoint("andromeda", 5051),
      ignoredNetworkInterfaces = Seq(existingNetInterface()),
      connectionRetryInterval = 10.seconds,
      externalEndpoint = Some(NetworkEndpoint("myexternal", 8765))
    )
    val cfg = SettingsMapping.toConfig(settings)
    cfg.getString("coinffeine.peer.id") shouldBe "1234"
    cfg.getInt("coinffeine.peer.port") shouldBe 5050
    cfg.getString("coinffeine.broker.hostname") shouldBe "andromeda"
    cfg.getInt("coinffeine.broker.port") shouldBe 5051
    cfg.getStringList("coinffeine.peer.ifaces.ignore") shouldBe
      seqAsJavaList(Seq(existingNetInterface().getName))
    cfg.getDuration("coinffeine.peer.connectionRetryInterval", TimeUnit.SECONDS) shouldBe 10
    cfg.getString("coinffeine.peer.externalHostname") shouldBe "myexternal"
    cfg.getInt("coinffeine.peer.externalPort") shouldBe 8765

    val cfg2 = SettingsMapping.toConfig(settings.copy(peerId = None))
    cfg2.getString("coinffeine.peer.id") shouldBe 'empty
  }

  "OKPay settings mapping" should "map from config" in {
    val conf = makeConfig(
      "coinffeine.okpay.id" -> "id",
      "coinffeine.okpay.token" -> "token",
      "coinffeine.okpay.endpoint" -> "http://example.com/death-star",
      "coinffeine.okpay.pollingInterval" -> "50s"
    )
    val settings = SettingsMapping.fromConfig[OkPaySettings](conf)
    settings.userAccount shouldBe Some("id")
    settings.seedToken shouldBe Some("token")
    settings.serverEndpoint shouldBe new URI("http://example.com/death-star")
    settings.pollingInterval shouldBe 50.seconds

    val settings2 = SettingsMapping.fromConfig[OkPaySettings](makeConfig(
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

  private def existingNetInterface(): NetworkInterface =
    NetworkInterface.getNetworkInterfaces.nextElement()
}
