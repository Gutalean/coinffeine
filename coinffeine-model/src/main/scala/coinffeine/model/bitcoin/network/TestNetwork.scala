package coinffeine.model.bitcoin.network

import org.bitcoinj.params.{RegTestParams, TestNet3Params}

import coinffeine.model.bitcoin.NetworkComponent
import coinffeine.model.network.NetworkEndpoint

object IntegrationTestNetwork extends RegTestParams with NetworkComponent.SeedPeers {
  dnsSeeds = Array.empty
  override val seedPeers = Seq(NetworkEndpoint("testnet.test.coinffeine.com", 19000))
  trait Component extends NetworkComponent {
    override lazy val network = IntegrationTestNetwork.this
  }
}

object PublicTestNetwork extends TestNet3Params with NetworkComponent.SeedPeers {
  dnsSeeds = Array.empty
  override val seedPeers =
    for (port <- 19000 to 19004) yield NetworkEndpoint("testnet.trial.coinffeine.com", port)
  trait Component extends NetworkComponent {
    override lazy val network = PublicTestNetwork.this
  }
}
