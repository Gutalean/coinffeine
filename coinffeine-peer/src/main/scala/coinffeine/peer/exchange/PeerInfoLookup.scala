package coinffeine.peer.exchange

import scala.concurrent.Future

import akka.actor.{ActorContext, ActorRef}

import coinffeine.common.akka.AskPattern
import coinffeine.model.exchange.Exchange
import coinffeine.model.payment.PaymentProcessor.AccountId
import coinffeine.peer.bitcoin.wallet.WalletActor
import coinffeine.peer.config.SettingsProvider
import coinffeine.peer.payment.okpay.OkPaySettings

trait PeerInfoLookup {
  /** Lookup current peer info */
  def lookup()(implicit context: ActorContext): Future[Exchange.PeerInfo]
}

class PeerInfoLookupImpl(wallet: ActorRef, lookupUserAccount: => Option[AccountId])
    extends PeerInfoLookup {

  import PeerInfoLookupImpl._

  def this(wallet: ActorRef, settingsProvider: SettingsProvider) =
    this(wallet, settingsProvider.okPaySettings().userAccount)

  override def lookup()(implicit context: ActorContext): Future[Exchange.PeerInfo] = {
    import context.dispatcher

    val creatingFreshKeyPair = AskPattern(
      to = wallet,
      request = WalletActor.CreateKeyPair,
      errorMessage = "Cannot get a fresh key pair"
    ).withImmediateReply[WalletActor.KeyPairCreated]().map(_.keyPair)

    val retrievingAccountId = lookupUserAccount.fold(missingId)(Future.successful)

    for {
      keyPair <- creatingFreshKeyPair
      accountId <- retrievingAccountId
    } yield Exchange.PeerInfo(accountId, keyPair)
  }

  private def missingId: Future[AccountId] = Future.failed(MissingOkPayWalletId)
}

object PeerInfoLookupImpl {
  object MissingOkPayWalletId extends Exception("missing OKPay wallet ID in app config")
}
