package coinffeine.peer.bitcoin

import scalaz.syntax.std.option._

import coinffeine.common.test.UnitTest

class BitcoinSettingsTest extends UnitTest {

  "Bitcoin settings" should "parse network names" in {
    BitcoinSettings.parseNetwork("integration-regnet") shouldBe
        BitcoinSettings.IntegrationRegnet.some
    BitcoinSettings.parseNetwork("mainnet") shouldBe BitcoinSettings.MainNet.some
  }

  it should "fail to parse unknown network names" in {
    BitcoinSettings.parseNetwork("public-testnet") shouldBe 'empty
  }
}
