package coinffeine.gui.setup

import scalafx.beans.property.{ObjectProperty, StringProperty}

import coinffeine.model.payment.okpay.VerificationStatus
import coinffeine.peer.payment.okpay.OkPayApiCredentials

class SetupConfig {

  val password: StringProperty = new StringProperty(this, "password")

  val okPayCredentials = new ObjectProperty[OkPayCredentials](this, "okPayCredentials")

  val okPayWalletAccess = new ObjectProperty[Option[OkPayApiCredentials]](
    this, "okPayWalletAccess", None)

  val okPayVerificationStatus =
    new ObjectProperty[Option[VerificationStatus]](this, "okPayVerificationStatus", None)
}
