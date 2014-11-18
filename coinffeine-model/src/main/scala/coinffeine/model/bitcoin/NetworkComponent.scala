package coinffeine.model.bitcoin

import coinffeine.model.network.NetworkEndpoint

trait NetworkComponent {
  def network: Network with NetworkComponent.SeedPeers
}

object NetworkComponent {
  trait SeedPeers {
    val seedPeers: Seq[NetworkEndpoint]
  }
}
