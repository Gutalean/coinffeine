package coinffeine.model.bitcoin

trait BitcoinProperties {
  val wallet: WalletProperties
  val network: NetworkProperties
}

class MutableBitcoinProperties extends BitcoinProperties {
  override val wallet = new MutableWalletProperties
  override val network = new MutableNetworkProperties
}

object MutableBitcoinProperties {

  trait Component {
    def bitcoinProperties: MutableBitcoinProperties
  }
}
