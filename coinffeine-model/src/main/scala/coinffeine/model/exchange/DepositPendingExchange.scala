package coinffeine.model.exchange

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.currency.FiatCurrency

case class DepositPendingExchange[C <: FiatCurrency](
    prev: HandshakingExchange[C],
    user: Exchange.PeerInfo,
    counterpart: Exchange.PeerInfo) extends AfterHandshakeExchange[C] {

  override val status = ExchangeStatus.WaitingDepositConfirmation
  override val metadata = prev.metadata
  override val progress = Exchange.noProgress(currency)
  override val isCompleted = false

  def startExchanging(deposits: Exchange.Deposits): RunningExchange[C] =
    RunningExchange(this, deposits)

  def cancel(cause: CancellationCause): FailedExchange[C] =
    FailedExchange(this, FailureCause.Cancellation(cause), Some(user))

  def abort(cause: AbortionCause, refundTx: ImmutableTransaction): AbortingExchange[C] =
    AbortingExchange(this, cause, user, refundTx)
}
