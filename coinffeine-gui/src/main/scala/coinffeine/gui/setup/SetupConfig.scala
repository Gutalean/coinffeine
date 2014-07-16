package coinffeine.gui.setup

import coinffeine.peer.payment.okpay.OkPayCredentials

/** Initial setup configuration.
  *
  * Note that all fields are optionals as the user is not forced to fill them in.
  *
  * @param password          Password to protect the application
  * @param okPayCredentials  OKPay credentials
  */
case class SetupConfig(password: Option[String], okPayCredentials: Option[OkPayCredentials])
