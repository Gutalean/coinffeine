package coinffeine.model.event

import coinffeine.model.bitcoin.BlockchainStatus

/** An event reporting the state of the connection with the bitcoin network. */
case class BitcoinConnectionStatus(activePeers: Int, blockchainStatus: BlockchainStatus)
  extends CoinffeineAppEvent {

  def connected: Boolean = activePeers > 0
}
