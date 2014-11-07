package coinffeine.peer.bitcoin

import org.bitcoinj.core.PeerGroup

import coinffeine.peer.bitcoin.wallet.SmartWallet

trait WalletComponent {

  def wallet(peerGroup: PeerGroup): SmartWallet
}
