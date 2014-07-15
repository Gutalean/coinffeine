package coinffeine.model.bitcoin

import com.google.bitcoin.core.AbstractBlockChain

trait BlockchainComponent {

  def blockchain: AbstractBlockChain
}
