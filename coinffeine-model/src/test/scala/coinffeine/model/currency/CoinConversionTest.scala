package coinffeine.model.currency

import org.bitcoinj.core.Coin
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks

import coinffeine.common.test.UnitTest

class CoinConversionTest extends UnitTest with PropertyChecks with Implicits {

  val MaxSatoshis = 21000000000L

  "Coin conversions" should "support roundtrip conversion" in {
    forAll(Gen.chooseNum(-MaxSatoshis, MaxSatoshis)) { satoshis =>
      val bitcoinAmount = Bitcoin.fromSatoshi(satoshis)
      val coinAmount = Coin.valueOf(satoshis)
      (bitcoinAmount: Coin) shouldBe coinAmount
      (coinAmount: BitcoinAmount) shouldBe bitcoinAmount
    }
  }
}
