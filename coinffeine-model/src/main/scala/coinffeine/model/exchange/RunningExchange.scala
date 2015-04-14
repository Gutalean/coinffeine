package coinffeine.model.exchange

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.currency.FiatCurrency

case class RunningExchange[C <: FiatCurrency](
    prev: DepositPendingExchange[C],
    deposits: Exchange.Deposits,
    progress: Exchange.Progress) extends AfterHandshakeExchange[C] {

  override val status = ExchangeStatus.Exchanging
  override val metadata = prev.metadata
  override val isCompleted = false
  override val user = prev.user
  override val counterpart = prev.counterpart

  def complete: SuccessfulExchange[C] = SuccessfulExchange(this)

  def panicked(transaction: ImmutableTransaction): FailedExchange[C] =
    FailedExchange(this, FailureCause.PanicBlockReached, Some(prev.user), Some(transaction))

  def stepFailure(step: Int,
                  cause: Throwable,
                  transaction: Option[ImmutableTransaction]): FailedExchange[C] =
    FailedExchange(this, FailureCause.StepFailed(step, cause), Some(prev.user), transaction)

  def unexpectedBroadcast(actualTransaction: ImmutableTransaction): FailedExchange[C] =
    FailedExchange(this, FailureCause.UnexpectedBroadcast, Some(prev.user), Some(actualTransaction))

  def noBroadcast: FailedExchange[C] =
    FailedExchange(this, FailureCause.NoBroadcast, Some(prev.user), transaction = None)

  def completeStep(step: Int): RunningExchange[C] =
    copy(progress = amounts.steps(step - 1).progress)
}

object RunningExchange {

  def apply[C <: FiatCurrency](prev: DepositPendingExchange[C],
                               deposits: Exchange.Deposits): RunningExchange[C] =
    RunningExchange(prev, deposits, prev.progress)
}
