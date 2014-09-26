package coinffeine.model.market

sealed trait OrderStatus {
  def name: String
  def isCancellable: Boolean
  def isFinal: Boolean

  override def toString: String = name
}

/** The order is offline.
  *
  * This status indicates that the corresponding order has been loaded in the system (either just
  * created or loaded from the disk) but it is not still in the market. No matching should be
  * expected while the order is in this state.
  */
case object OfflineOrder extends OrderStatus {
  override val name = "offline"
  override val isCancellable = true
  override def isFinal = false
}

/** The order is in the market.
  *
  * This status indicates that the corresponding order has been placed in the market.
  *
  */
case object InMarketOrder extends OrderStatus {
  override val name = "in market"
  override val isCancellable = true
  override def isFinal = false
}

/** The order is in progress.
  *
  * This status indicates that there are some order matches and therefore some exchanges running.
  */
case object InProgressOrder extends OrderStatus {
  override val name ="in progress"
  override val isCancellable = true
  override def isFinal = false
}

/** The order is completed.
  *
  * This status indicates that the corresponding order has been completed. All the funds have
  * been successfully transferred.
  */
case object CompletedOrder extends OrderStatus {
  override val name = "completed"
  override val isCancellable = false
  override def isFinal = true
}

/** The order is cancelled.
  *
  * This status indicates that the corresponding order has been cancelled. The funds that were
  * already transferred cannot be moved again.
  *
  * @param reason The reason why the order was cancelled
  */
case class CancelledOrder(reason: String) extends OrderStatus {
  override val name = s"cancelled ($reason)"
  override val isCancellable = false
  override def isFinal = true
}


