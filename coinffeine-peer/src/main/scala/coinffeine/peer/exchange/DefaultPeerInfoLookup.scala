package coinffeine.peer.exchange

import scala.concurrent.Future

import akka.actor.{ActorContext, ActorRef}

import coinffeine.common.akka.AskPattern
import coinffeine.model.exchange.Exchange
import coinffeine.peer.bitcoin.wallet.WalletActor
import coinffeine.peer.payment.PaymentProcessorActor

class DefaultPeerInfoLookup(wallet: ActorRef, paymentProcessor: ActorRef) extends PeerInfoLookup {

  override def lookup()(implicit context: ActorContext): Future[Exchange.PeerInfo] = {
    import context.dispatcher

    val creatingFreshKeyPair = AskPattern(
      to = wallet,
      request = WalletActor.CreateKeyPair,
      errorMessage = "Cannot get a fresh key pair"
    ).withImmediateReply[WalletActor.KeyPairCreated]().map(_.keyPair)

    val retrievingAccountId = AskPattern(
      to = paymentProcessor,
      request = PaymentProcessorActor.RetrieveAccountId,
      errorMessage = "Cannot retrieve the user account id"
    ).withImmediateReply[PaymentProcessorActor.RetrievedAccountId]().map(_.id)

    for {
      keyPair <- creatingFreshKeyPair
      accountId <- retrievingAccountId
    } yield Exchange.PeerInfo(accountId, keyPair)
  }
}
