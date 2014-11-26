package coinffeine.model.market

sealed trait OrderStatus {
  def name: String

  /** True if the order is considered active in the network
    * (not completed, terminated, cancelled, etc).
    */
  def isActive: Boolean

  override def toString: String = name
}

case object NotStartedOrder extends OrderStatus {
  override val name = "not started"
  override val isActive = true
}

/** The order is offline.
  *
  * This status indicates that the corresponding order has been loaded in the system (either just
  * created or loaded from the disk) but it is not still in the market. No matching should be
  * expected while the order is in this state.
  */
case object OfflineOrder extends OrderStatus {
  override val name = "offline"
  override val isActive = true
}

/** The order is in the market.
  *
  * This status indicates that the corresponding order has been placed in the market.
  *
  */
case object InMarketOrder extends OrderStatus {
  override val name = "in market"
  override val isActive = true
}

/** The order is in progress.
  *
  * This status indicates that there are some order matches and therefore some exchanges running.
  */
case object InProgressOrder extends OrderStatus {
  override val name ="in progress"
  override val isActive = true
}

/** The order is completed.
  *
  * This status indicates that the corresponding order has been completed. All the funds have
  * been successfully transferred.
  */
case object CompletedOrder extends OrderStatus {
  override val name = "completed"
  override val isActive = false
}

/** The order is cancelled.
  *
  * This status indicates that the corresponding order has been cancelled. The funds that were
  * already transferred cannot be moved again.
  */
case object CancelledOrder extends OrderStatus {
  override val name = "cancelled"
  override val isActive = false
}


