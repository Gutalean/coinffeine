package coinffeine.model.exchange

import org.joda.time.DateTime

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.currency.FiatCurrency

case class RunningExchange(
    prev: DepositPendingExchange,
    timestamp: DateTime,
    deposits: ActiveExchange.Deposits,
    progress: Exchange.Progress) extends AfterHandshakeExchange {

  override val status = ExchangeStatus.Exchanging(deposits.map(_.get.getHash))
  override val metadata = prev.metadata
  override val isCompleted = false
  override val user = prev.user
  override val counterpart = prev.counterpart

  override lazy val log = prev.log.record(status, timestamp)

  def complete(timestamp: DateTime): SuccessfulExchange =
    SuccessfulExchange(this, timestamp)

  def panicked(transaction: ImmutableTransaction,
               timestamp: DateTime): FailedExchange = {
    val cause = FailureCause.PanicBlockReached
    FailedExchange(this, timestamp, cause, Some(prev.user), Some(transaction))
  }

  def stepFailure(step: Int,
                  transaction: Option[ImmutableTransaction],
                  timestamp: DateTime): FailedExchange = FailedExchange(
    this, timestamp, FailureCause.StepFailed(step), Some(prev.user), transaction)

  def unexpectedBroadcast(actualTransaction: ImmutableTransaction,
                          timestamp: DateTime): FailedExchange = {
    val cause = FailureCause.UnexpectedBroadcast
    FailedExchange(this, timestamp, cause, Some(prev.user), Some(actualTransaction))
  }

  def noBroadcast(timestamp: DateTime): FailedExchange =
    FailedExchange(this, timestamp, FailureCause.NoBroadcast, Some(prev.user), transaction = None)

  def completeStep(step: Int): RunningExchange =
    copy(progress = amounts.steps(step - 1).progress)
}

object RunningExchange {

  def apply(prev: DepositPendingExchange,
                               deposits: ActiveExchange.Deposits,
                               timestamp: DateTime): RunningExchange =
    RunningExchange(prev, timestamp, deposits, prev.progress)
}
