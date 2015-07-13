package coinffeine.model.exchange

import org.joda.time.DateTime

import coinffeine.model.exchange.Exchange.Progress

case class SuccessfulExchange(prev: RunningExchange, timestamp: DateTime)
  extends AfterHandshakeExchange with CompletedExchange {

  override val status = ExchangeStatus.Successful
  override val metadata = prev.metadata
  override val progress = Progress(amounts.exchangedBitcoin)
  override val isSuccess = true
  override val user = prev.user
  override val counterpart = prev.counterpart

  override lazy val log = prev.log.record(status, timestamp)
}
