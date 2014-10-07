package coinffeine.peer.config

import java.io.File
import java.net.{URI, NetworkInterface}
import java.util.concurrent.TimeUnit
import scala.collection.JavaConversions._
import scala.concurrent.duration._

import com.typesafe.config.{ConfigValueFactory, ConfigFactory, Config}

import coinffeine.common.test.UnitTest
import coinffeine.peer.bitcoin.BitcoinSettings
import coinffeine.peer.payment.okpay.OkPaySettings
import coinffeine.protocol.MessageGatewaySettings

class SettingsMappingTest extends UnitTest {

  import SettingsMapping._

  "Bitcoins settings mapping" should "map from config" in {
    val conf = makeConfig(
      "coinffeine.bitcoin.connectionRetryInterval" -> "30s",
      "coinffeine.bitcoin.walletFile" -> "user.wallet",
      "coinffeine.bitcoin.rebroadcastTimeout" -> "60s"
    )
    SettingsMapping.fromConfig[BitcoinSettings](conf) shouldBe BitcoinSettings(
      connectionRetryInterval = 30.seconds,
      walletFile = new File("user.wallet"),
      rebroadcastTimeout = 1.minute
    )
  }

  it should "map to config" in {
    val settings = BitcoinSettings(
      connectionRetryInterval = 50.seconds,
      walletFile = new File("/tmp/user.wallet"),
      rebroadcastTimeout = 60.seconds
    )
    val cfg = SettingsMapping.toConfig(settings)
    cfg.getDuration("coinffeine.bitcoin.connectionRetryInterval", TimeUnit.SECONDS) shouldBe 50
    cfg.getString("coinffeine.bitcoin.walletFile") should be (new File("/tmp/user.wallet").getPath)
    cfg.getDuration("coinffeine.bitcoin.rebroadcastTimeout", TimeUnit.SECONDS) shouldBe 60
  }

  "Message Gateway settings mapping" should "map from config" in {
    val conf = makeConfig(
      "coinffeine.peer.port" -> 5000,
      "coinffeine.broker.hostname" -> "broker-host",
      "coinffeine.broker.port" -> 4000,
      "coinffeine.peer.ifaces.ignore" -> asJavaIterable(Seq(existingNetInterface().getName))
    )
    val settings = SettingsMapping.fromConfig[MessageGatewaySettings](conf)
    settings.peerPort should be (5000)
    settings.brokerHost should be ("broker-host")
    settings.brokerPort should be (4000)
    settings.ignoredNetworkInterfaces should be (Seq(existingNetInterface()))
  }

  it should "map to config" in {
    val settings = MessageGatewaySettings(
      peerPort = 5050,
      brokerHost = "andromeda",
      brokerPort = 5051,
      ignoredNetworkInterfaces = Seq(existingNetInterface())
    )
    val cfg = SettingsMapping.toConfig(settings)
    cfg.getInt("coinffeine.peer.port") should be (5050)
    cfg.getString("coinffeine.broker.hostname") should be ("andromeda")
    cfg.getInt("coinffeine.broker.port") should be (5051)
    cfg.getStringList("coinffeine.peer.ifaces.ignore") should be (
      seqAsJavaList(Seq(existingNetInterface().getName)))
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

  private def makeConfig(items: (String, Any)*): Config = items.foldLeft(ConfigFactory.empty()) {
    case (config, (configPath, configValue)) =>
      config.withValue(configPath, ConfigValueFactory.fromAnyRef(configValue))
  }

  private def existingNetInterface(): NetworkInterface =
    NetworkInterface.getNetworkInterfaces.nextElement()
}
