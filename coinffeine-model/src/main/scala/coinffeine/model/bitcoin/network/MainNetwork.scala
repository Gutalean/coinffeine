package coinffeine.model.bitcoin.network

import org.bitcoinj.params.MainNetParams

import coinffeine.model.bitcoin.NetworkComponent

object MainNetwork extends NetworkComponent {
  override lazy val network = MainNetParams.get
  /** No handpicked peers, use DNS bootstrap */
  override val peerAddresses = Seq.empty
}
