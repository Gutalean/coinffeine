package coinffeine.peer.bitcoin

import com.typesafe.config.ConfigFactory

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.peer.config.ConfigComponent

class DummyPrivateKeysComponentTest extends UnitTest {

  class TestComponent(rawConfig: String) extends DummyPrivateKeysComponent
    with CoinffeineUnitTestNetwork.Component with ConfigComponent {
    override val config = ConfigFactory.parseString(rawConfig)
  }

  "A dummy wallet component" should "provide a wallet with a private key taken from the config" in {
    val component = new TestComponent(
      """coinffeine.wallet.key="cPbLnyzaxQeqWoaDyFmEgBVMoee7jXbHcCs1rQD7Ltj6d9Rqn8Uy" """)

    component.keyPairs should have length 1
    val key = component.keyPairs(0)
    key.toAddress(CoinffeineUnitTestNetwork).toString should be ("n2bkzKcSGgv7uprYdwmxtj2R9th2BTozKq")
  }
}
