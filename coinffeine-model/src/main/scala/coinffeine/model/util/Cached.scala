package coinffeine.model.util

case class Cached[T](cached: T, status: CacheStatus)

object Cached {
  def fresh[T](value: T) = Cached(value, CacheStatus.Fresh)

  def stale[T](value: T) = Cached(value, CacheStatus.Stale)
}

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
