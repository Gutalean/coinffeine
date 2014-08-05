package coinffeine.model.market

sealed trait OrderStatus {
  def name: String
  def isCancellable: Boolean

  override def toString: String = name
}

/** The order is stalled.
  *
  * This status indicates that the corresponding order has been stalled since it is waiting for
  * funds.
  */
case object StalledOrder extends OrderStatus {
  override val name = s"stalled"
  override val isCancellable = true
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
}

/** The order is in the market.
  *
  * This status indicates that the corresponding order has been placed in the market.
  *
  */
case object InMarketOrder extends OrderStatus {
  override val name = "in market"
  override val isCancellable = true
}

/** The order is completed.
  *
  * This status indicates that the corresponding order has been completed. All the funds have
  * been successfully transferred.
  */
case object CompletedOrder extends OrderStatus {
  override val name = "completed"
  override val isCancellable = false
}

/** The order is cancelled.
  *
  * This status indicates that the corresponding order has been cancelled. The funds that were
  * already transferred cannot be moved again.
  *
  * @param reason The reason because of the order was cancelled
  */
case class CancelledOrder(reason: String) extends OrderStatus {
  override val name = s"cancelled ($reason)"
  override val isCancellable = false
}


