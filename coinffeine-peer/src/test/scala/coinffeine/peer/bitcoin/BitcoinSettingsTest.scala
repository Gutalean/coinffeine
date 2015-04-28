package coinffeine.peer.bitcoin

import coinffeine.common.test.UnitTest

class BitcoinSettingsTest extends UnitTest {

  "Bitcoin settings" should "parse network names" in {
    BitcoinSettings.parseNetwork("public-testnet") shouldBe BitcoinSettings.PublicTestnet
    BitcoinSettings.parseNetwork("integration-regnet") shouldBe BitcoinSettings.IntegrationRegnet
    BitcoinSettings.parseNetwork("mainnet") shouldBe BitcoinSettings.MainNet
  }

  it should "fail to parse unknown network names" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      BitcoinSettings.parseNetwork("foocoin")
    }
  }
}
