package coinffeine.model.bitcoin.network

import org.bitcoinj.params.RegTestParams

import coinffeine.model.bitcoin.NetworkComponent
import coinffeine.model.network.NetworkEndpoint

trait TestNetwork extends RegTestParams with NetworkComponent.SeedPeers {
  trait Component extends NetworkComponent {
    override lazy val network = TestNetwork.this
  }
}

object IntegrationTestNetwork extends TestNetwork {
  dnsSeeds = Array.empty
  override val seedPeers = Seq(NetworkEndpoint("testnet.test.coinffeine.com", 19000))
}

object PublicTestNetwork extends TestNetwork {
  dnsSeeds = Array.empty
  override val seedPeers =
    for (port <- 19000 to 19004) yield NetworkEndpoint("testnet.trial.coinffeine.com", port)
}
