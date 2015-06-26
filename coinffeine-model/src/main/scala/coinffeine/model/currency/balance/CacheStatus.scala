package coinffeine.model.currency.balance

sealed trait CacheStatus {
  def isFresh: Boolean
}

object CacheStatus {

  case object Fresh extends CacheStatus {
    override def isFresh = true
  }

  case object Stale extends CacheStatus {
    override def isFresh = false
  }

}
