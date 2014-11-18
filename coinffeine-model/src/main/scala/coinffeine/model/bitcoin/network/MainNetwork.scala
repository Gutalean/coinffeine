package coinffeine.model.bitcoin.network

import org.bitcoinj.params.MainNetParams

import coinffeine.model.bitcoin.NetworkComponent

object MainNetwork extends NetworkComponent {
  override lazy val network = new MainNetParams with NetworkComponent.SeedPeers {
    /** No handpicked peers, use DNS bootstrap */
    override val seedPeers = Seq.empty
  }
}
