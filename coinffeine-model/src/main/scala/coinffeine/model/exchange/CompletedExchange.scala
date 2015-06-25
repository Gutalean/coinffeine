package coinffeine.model.exchange

trait CompletedExchange extends ActiveExchange {

  override val isCompleted = true
  override val isStarted = true
  def isSuccess: Boolean
}
