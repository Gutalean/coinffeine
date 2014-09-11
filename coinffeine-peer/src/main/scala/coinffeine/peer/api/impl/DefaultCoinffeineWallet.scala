package coinffeine.peer.api.impl

import scala.concurrent.{Future, TimeoutException}

import akka.actor.ActorRef
import akka.pattern._

import coinffeine.model.bitcoin.{WalletProperties, Address, Hash, KeyPair}
import coinffeine.model.currency.BitcoinAmount
import coinffeine.peer.CoinffeinePeerActor.{RetrieveWalletBalance, WalletBalance}
import coinffeine.peer.api.CoinffeineWallet

private[impl] class DefaultCoinffeineWallet(properties: WalletProperties) extends CoinffeineWallet {

  override val balance = properties.balance
  override val primaryKeyPair = properties.primaryKeyPair

  override def transfer(amount: BitcoinAmount, address: Address): Future[Hash] = ???
  override def importPrivateKey(address: Address, key: KeyPair): Unit = ???
}
