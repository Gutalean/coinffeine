package coinffeine.peer.payment.okpay.ws

/** OKPay API soap faults
  *
  * @see https://dev.okpay.com/es/manual/interfaces/error-codes.html
  */
object OkPayFault extends Enumeration {
  type OkPayFault = Value

  // Authentication and Security
  val DisabledApi = Value("API_Disabled")
  val DisabledApiFunction = Value("API_Function_Disabled")
  val InvalidIpAddress = Value("Invalid_IP_Address")
  val AuthenticationFailed = Value("Authentication_Failed")
  val DisabledAccountApi = Value("Account_API_Disabled")
  val AccessDenied = Value("Access_Denied")
  val InternalError = Value("Internal_Error")

  // Payments and History
  val UnsupportedPaymentMethod = Value("Payment_Method_Not_Supported")
  val BlockedSourceAction = Value("Source_Account_Blocked")
  val BlockedTargetAccount = Value("Target_Account_Blocked")
  val ReceiverNotFound = Value("Receiver_Not_Found")
  val TransactionNotFound = Value("Transaction_Not_Found")
  val DisabledCurrency = Value("Currency_Disabled")
  val CurrencyNotFound = Value("Currency_Not_Found")
  val IncorrectAmount = Value("Incorrect_Amount")
  val NotEnoughMoney = Value("Not_Enough_Money")
  val LimitsExceeded = Value("Limits_Exceeded")
  val MoneyTransferToYourself = Value("MoneyTransfer_To_Yourself")
  val DuplicatePayment = Value("Duplicate_Payment")
  val IncorrectEmail = Value("Incorrect_Email")
  val ObsoleteFunction = Value("Function_Is_Obsolete")
  val TooLargeAmount = Value("Maximum_Amount_Is_")
  val TooSmallAmount = Value("Minimum_Amount_Is_")
  val IncorrectAccount = Value("Incorrect_Account")
  val IncorrectParameters = Value("Incorrect_Parameters")
  val UnsupportedCurrency = Value("Currency_Not_Supported")

  // Client Verification
  val AccountNotFound = Value("Account_Not_Found")
  val ClientNotFound = Value("Client_Not_Found")
  val ImageNotFound = Value("Image_Not_Found")
  val ClientAccessDenied = Value("Client_Access_Denied")

  // Subscriptions
  val IncompatibleSubscriptionStatus = Value("Incompatible_Subscription_Status")
  val SubscriptionNotFound = Value("Subscription_Not_Found")

  //Pre-approved Payments
  val IncompatiblePreApprovedPaymentStatus = Value("Incompatible_PreapprovedPayment_Status")
  val PreApprovedPaymentNotFound = Value("PreapprovedPayment_Not_Found")

  def valueOf(code: String): Option[OkPayFault] = values.find(_.toString == code)

  def unapply(code: String): Option[OkPayFault] = valueOf(code)
}
