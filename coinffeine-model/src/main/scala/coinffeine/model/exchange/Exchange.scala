package coinffeine.model.exchange

import org.joda.time.DateTime

import coinffeine.model.bitcoin._
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeStatus.Exchanging
import coinffeine.model.network.PeerId
import coinffeine.model.payment.PaymentProcessor
import coinffeine.model.{ActivityLog, Both}

case class ExchangeMetadata(
  id: ExchangeId,
  role: Role,
  counterpartId: PeerId,
  amounts: ActiveExchange.Amounts,
  parameters: ActiveExchange.Parameters,
  createdOn: DateTime)

trait Exchange {
  def id: ExchangeId
  def role: Role
  def status: ExchangeStatus
  def log: ActivityLog[ExchangeStatus]
  def progress: Exchange.Progress
  def isCompleted: Boolean
  def isStarted: Boolean

  def counterpartId: PeerId
  def exchangedBitcoin: Both[BitcoinAmount]
  def exchangedFiat: Both[FiatAmount]
  def currency: FiatCurrency = exchangedFiat.buyer.currency
  def lockTime: Long

  def depositsIds: Option[Both[Hash]] = log.activities.collectFirst {
    case ActivityLog.Entry(Exchanging(ids), _) => ids
  }
}

object Exchange {

  case class PeerInfo(paymentProcessorAccount: PaymentProcessor.AccountId, bitcoinKey: KeyPair) {
    override def equals(obj: scala.Any) = obj match {
      case other: PeerInfo =>
        paymentProcessorAccount == other.paymentProcessorAccount &&
          KeyPairUtils.equals(bitcoinKey, other.bitcoinKey)
      case _ => false
    }
  }

  case class Progress(bitcoinsTransferred: Both[BitcoinAmount]) {
    def +(other: Progress) =
      Progress(bitcoinsTransferred.zip(other.bitcoinsTransferred).map { case (l, r) => l + r })
  }

  def noProgress = Exchange.Progress(Both.fill(Bitcoin.zero))
}
