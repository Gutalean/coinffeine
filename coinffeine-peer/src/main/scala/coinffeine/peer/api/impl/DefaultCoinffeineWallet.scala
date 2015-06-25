package coinffeine.peer.api.impl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import akka.actor.ActorRef

import coinffeine.common.akka.AskPattern
import coinffeine.model.bitcoin.{Address, Hash, WalletProperties}
import coinffeine.model.currency.BitcoinAmount
import coinffeine.peer.CoinffeinePeerActor
import coinffeine.peer.api.CoinffeineWallet

private[impl] class DefaultCoinffeineWallet(
    properties: WalletProperties, peer: ActorRef) extends CoinffeineWallet {

  override val balance = properties.balance
  override val primaryAddress = properties.primaryAddress
  override val activity = properties.activity

  override def transfer(amount: BitcoinAmount, address: Address): Future[Hash] = {
    val request = CoinffeinePeerActor.WithdrawWalletFunds(amount, address)
    AskPattern(peer, request)
      .withImmediateReply[CoinffeinePeerActor.WalletFundsWithdrawn]
      .map(_.tx.get.getHash)
  }
}
