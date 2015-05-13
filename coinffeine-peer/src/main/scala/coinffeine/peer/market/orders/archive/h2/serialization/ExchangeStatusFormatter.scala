package coinffeine.peer.market.orders.archive.h2.serialization

import coinffeine.model.Both
import coinffeine.model.exchange.AbortionCause.InvalidCommitments
import coinffeine.model.exchange.CancellationCause.HandshakeFailed
import coinffeine.model.exchange.Exchange.PeerInfo
import coinffeine.model.exchange.FailureCause.{Abortion, Cancellation, StepFailed}
import coinffeine.model.exchange.{AbortionCause, CancellationCause, ExchangeStatus}

private[h2] object ExchangeStatusFormatter {

  def format(exchangeStatus: ExchangeStatus): String = exchangeStatus match {

    case ExchangeStatus.WaitingDepositConfirmation(user, counterpart) =>
      "WaitingDepositConfirmation(%s,%s)".format(format(user), format(counterpart))

    case ExchangeStatus.Exchanging(Both(buyerDeposit, sellerDeposit)) =>
      """Exchanging("%s","%s")""".format(buyerDeposit, sellerDeposit)

    case ExchangeStatus.Failed(Cancellation(cause)) => s"Failed(Cancellation(${format(cause)}))"
    case ExchangeStatus.Failed(Abortion(cause)) => s"Failed(Abortion(${format(cause)}))"
    case ExchangeStatus.Failed(StepFailed(step)) => s"Failed(StepFailed($step))"
    case ExchangeStatus.Failed(cause: Product) => s"Failed(${cause.productPrefix})"

    case ExchangeStatus.Aborting(cause) => s"Aborting(${format(cause)})"

    case _ => exchangeStatus.toString
  }

  private def format(peerInfo: PeerInfo): String = """PeerInfo("%s","%s")""".format(
    peerInfo.paymentProcessorAccount,
    org.bitcoinj.core.Utils.HEX.encode(peerInfo.bitcoinKey.getPubKey)
  )

  private def format(cause: CancellationCause): String = cause match {
    case HandshakeFailed(handshakeFailureCause) => s"HandshakeFailed($handshakeFailureCause)"
    case p: Product => p.productPrefix
  }

  private def format(cause: AbortionCause): String = cause match {
    case InvalidCommitments(Both(invalidBuyer, invalidSeller)) =>
      s"InvalidCommitments($invalidBuyer,$invalidSeller)"
    case p: Product => p.productPrefix
  }
}
