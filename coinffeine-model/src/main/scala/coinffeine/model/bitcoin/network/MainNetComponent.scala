package coinffeine.model.bitcoin.network

import org.bitcoinj.params.MainNetParams

import coinffeine.model.bitcoin.NetworkComponent

trait MainNetComponent extends NetworkComponent {
  override lazy val network: MainNetParams = MainNetParams.get
}
