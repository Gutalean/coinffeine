package coinffeine.model.bitcoin

import com.google.bitcoin.params.TestNet3Params

trait IntegrationTestNetworkComponent extends NetworkComponent {
  override lazy val network = new TestNet3Params() {
    dnsSeeds = Array.empty
  }
}
