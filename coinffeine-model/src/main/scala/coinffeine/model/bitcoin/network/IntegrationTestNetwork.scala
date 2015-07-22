package coinffeine.model.bitcoin.network

import org.bitcoinj.params.RegTestParams

import coinffeine.model.bitcoin.NetworkComponent
import coinffeine.model.network.NetworkEndpoint

object IntegrationTestNetwork extends RegTestParams with NetworkComponent.SeedPeers {
  dnsSeeds = Array.empty
  override val seedPeers = Seq(NetworkEndpoint("testnet.test.coinffeine.com", 19000))
  trait Component extends NetworkComponent {
    override lazy val network = IntegrationTestNetwork.this
  }
}
