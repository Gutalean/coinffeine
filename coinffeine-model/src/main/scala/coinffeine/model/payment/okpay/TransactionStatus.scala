package coinffeine.model.payment.okpay

sealed trait TransactionStatus {
  def isCompleted: Boolean = false
  def affectsLimits: Boolean = true
}

case object TransactionStatus {

  case object None extends TransactionStatus

  case object Error extends TransactionStatus

  case object Canceled extends TransactionStatus {
    override def affectsLimits = false
  }

  case object Pending extends TransactionStatus

  case object Reversed extends TransactionStatus {
    override def affectsLimits = false
  }

  case object Hold extends TransactionStatus

  case object Completed extends TransactionStatus {
    override def isCompleted = true
  }

}
