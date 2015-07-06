package coinffeine.model.payment.okpay

sealed trait TransactionStatus

case object TransactionStatus {

  case object None extends TransactionStatus

  case object Error extends TransactionStatus

  case object Canceled extends TransactionStatus

  case object Pending extends TransactionStatus

  case object Reversed extends TransactionStatus

  case object Hold extends TransactionStatus

  case object Completed extends TransactionStatus

}
