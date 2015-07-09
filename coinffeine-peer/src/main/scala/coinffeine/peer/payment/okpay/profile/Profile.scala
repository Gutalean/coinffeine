package coinffeine.peer.payment.okpay.profile

import coinffeine.model.payment.okpay.VerificationStatus

/** Interface to manipulate the OKPay profile */
trait Profile {

  def accountMode: AccountMode
  def accountMode_=(newAccountMode: AccountMode): Unit

  def walletId(): String

  def enableAPI(walletId: String): Unit

  def configureSeedToken(walletId: String): String

  def verificationStatus: VerificationStatus
}
