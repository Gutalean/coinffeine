package coinffeine.model.order

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


