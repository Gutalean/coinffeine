package coinffeine.peer.bitcoin

import coinffeine.peer.bitcoin.wallet.SmartWallet

trait WalletComponent {

  def wallet: SmartWallet
}
