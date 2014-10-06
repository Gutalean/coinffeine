package coinffeine.model.bitcoin

import org.bitcoinj.params.MainNetParams

trait MainNetComponent extends NetworkComponent {
  override lazy val network: MainNetParams = MainNetParams.get
}
