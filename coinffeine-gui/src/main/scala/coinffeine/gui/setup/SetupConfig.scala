package coinffeine.gui.setup

import coinffeine.peer.payment.okpay.{OkPayWalletAccess, OkPayCredentials}

/** Initial setup configuration.
  *
  * Note that all fields are optionals as the user is not forced to fill them in.
  *
  * @param password          Password to protect the application
  * @param okPayCredentials  OKPay credentials
  * @param okPayWalletAccess OKPay wallet access
  */
case class SetupConfig(
  password: Option[String],
  okPayCredentials: Option[OkPayCredentials],
  okPayWalletAccess: Option[OkPayWalletAccess])
