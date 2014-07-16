package coinffeine.peer.bitcoin

import coinffeine.model.bitcoin.Wallet

trait WalletComponent {
  def wallet: Wallet
}
