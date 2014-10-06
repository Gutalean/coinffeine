package coinffeine.model.bitcoin

import org.bitcoinj.core.AbstractBlockChain

trait BlockchainComponent {

  def blockchain: AbstractBlockChain
}
