package coinffeine.peer.api.impl

import scala.concurrent.Future

import akka.actor.ActorRef
import akka.pattern._

import coinffeine.model.bitcoin.{Address, Hash, KeyPair}
import coinffeine.model.currency.BitcoinAmount
import coinffeine.peer.CoinffeinePeerActor.{RetrieveWalletBalance, WalletBalance}
import coinffeine.peer.api.CoinffeineWallet

private[impl] class DefaultCoinffeineWallet(override val peer: ActorRef)
  extends CoinffeineWallet with PeerActorWrapper {

  override def currentBalance(): BitcoinAmount =
    await((peer ? RetrieveWalletBalance).mapTo[WalletBalance]).amount

  override def transfer(amount: BitcoinAmount, address: Address): Future[Hash] = ???
  override def importPrivateKey(address: Address, key: KeyPair): Unit = ???
  override def depositAddress: Address = ???
}
