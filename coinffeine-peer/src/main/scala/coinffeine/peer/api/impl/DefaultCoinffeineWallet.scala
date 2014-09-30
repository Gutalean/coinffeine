package coinffeine.peer.api.impl

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor.ActorRef

import coinffeine.common.akka.AskPattern
import coinffeine.model.bitcoin.{WalletProperties, Address, Hash, KeyPair}
import coinffeine.model.currency.Bitcoin
import coinffeine.peer.CoinffeinePeerActor
import coinffeine.peer.api.CoinffeineWallet

private[impl] class DefaultCoinffeineWallet(
    properties: WalletProperties, peer: ActorRef) extends CoinffeineWallet {

  override val balance = properties.balance
  override val primaryAddress = properties.primaryAddress
  override val activity = properties.activity

  override def transfer(amount: Bitcoin.Amount, address: Address): Future[Hash] = {
    val request = CoinffeinePeerActor.WithdrawWalletFunds(amount, address)
    AskPattern(peer, request)
      .withImmediateReply[CoinffeinePeerActor.WalletFundsWithdrawn]
      .map(_.tx.get.getHash)
  }
  override def importPrivateKey(address: Address, key: KeyPair): Unit = ???
}
