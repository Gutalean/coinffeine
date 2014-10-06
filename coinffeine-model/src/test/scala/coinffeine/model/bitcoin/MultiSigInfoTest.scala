package coinffeine.model.bitcoin

import scala.collection.JavaConversions._

import org.bitcoinj.script.ScriptBuilder

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.test.{CoinffeineUnitTestNetwork => Network}

class MultiSigInfoTest extends UnitTest {

  "MultiSigInfo" should "read all the parameters of a multisig output script" in {
    val keys = List.fill(10)(new PublicKey())
    val requiredKeys = 7
    val script = ScriptBuilder.createMultiSigOutputScript(requiredKeys, keys)
    val multiSigInfo = MultiSigInfo.fromScript(script).get
    multiSigInfo.possibleKeys should have size keys.size
    multiSigInfo.requiredKeyCount shouldBe requiredKeys
    multiSigInfo.possibleKeys.map(_.toAddress(Network)) shouldBe keys.map(_.toAddress(Network))
  }

  it should "fail on non-multisig scripts" in {
    val script = ScriptBuilder.createOutputScript(new PublicKey())
    MultiSigInfo.fromScript(script) shouldBe 'empty
  }

  it should "fail on input multisig scripts" in {
    val script = ScriptBuilder.createMultiSigInputScript(List(
      new TransactionSignature(BigInt(0).underlying(), BigInt(0).underlying())))
    MultiSigInfo.fromScript(script) shouldBe 'empty
  }
}
