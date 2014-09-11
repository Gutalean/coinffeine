package coinffeine.model.bitcoin

trait BitcoinProperties {
  def wallet: WalletProperties
  def network: NetworkProperties
}

class MutableBitcoinProperties extends BitcoinProperties {
  override val wallet = new MutableWalletProperties
  override val network = new MutableNetworkProperties
}
