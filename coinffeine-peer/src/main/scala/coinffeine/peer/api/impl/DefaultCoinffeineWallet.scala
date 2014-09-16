package coinffeine.peer.api.impl

import scala.concurrent.Future

import coinffeine.model.bitcoin.{WalletProperties, Address, Hash, KeyPair}
import coinffeine.model.currency.BitcoinAmount
import coinffeine.peer.api.CoinffeineWallet

private[impl] class DefaultCoinffeineWallet(properties: WalletProperties) extends CoinffeineWallet {

  override val balance = properties.balance
  override val primaryAddress = properties.primaryAddress

  override def transfer(amount: BitcoinAmount, address: Address): Future[Hash] = ???
  override def importPrivateKey(address: Address, key: KeyPair): Unit = ???
}
