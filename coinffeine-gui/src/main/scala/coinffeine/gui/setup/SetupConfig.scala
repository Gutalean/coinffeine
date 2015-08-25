package coinffeine.gui.setup

import scalafx.beans.property.{ObjectProperty, StringProperty}

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.payment.okpay.VerificationStatus
import coinffeine.peer.payment.okpay.OkPayApiCredentials

class SetupConfig {

  val currency: ObjectProperty[Option[FiatCurrency]] =
    new ObjectProperty(this, "currency", None)

  val okPayCredentials = new ObjectProperty[OkPayCredentials](this, "okPayCredentials")

  val okPayWalletAccess = new ObjectProperty[Option[OkPayApiCredentials]](
    this, "okPayWalletAccess", None)

  val okPayVerificationStatus =
    new ObjectProperty[Option[VerificationStatus]](this, "okPayVerificationStatus", None)
}
