package coinffeine.peer.exchange

import scala.concurrent.Future

import akka.actor.{ActorContext, ActorRef}

import coinffeine.common.akka.AskPattern
import coinffeine.model.exchange.Exchange
import coinffeine.model.payment.PaymentProcessor.AccountId
import coinffeine.peer.bitcoin.wallet.WalletActor
import coinffeine.peer.payment.okpay.OkPaySettings

trait PeerInfoLookup {
  /** Lookup current peer info */
  def lookup()(implicit context: ActorContext): Future[Exchange.PeerInfo]
}

class PeerInfoLookupImpl(wallet: ActorRef, lookupSettings: () => OkPaySettings) extends PeerInfoLookup {

  import PeerInfoLookupImpl._

  override def lookup()(implicit context: ActorContext): Future[Exchange.PeerInfo] = {
    import context.dispatcher

    val creatingFreshKeyPair = AskPattern(
      to = wallet,
      request = WalletActor.CreateKeyPair,
      errorMessage = "Cannot get a fresh key pair"
    ).withImmediateReply[WalletActor.KeyPairCreated]().map(_.keyPair)

    val retrievingAccountId = lookupSettings().userAccount
      .fold[Future[AccountId]](Future.failed(MissingOkPayWalletId))(Future.successful)

    for {
      keyPair <- creatingFreshKeyPair
      accountId <- retrievingAccountId
    } yield Exchange.PeerInfo(accountId, keyPair)
  }
}

object PeerInfoLookupImpl {
  object MissingOkPayWalletId extends Exception("missing OKPay wallet ID in app config")
}
