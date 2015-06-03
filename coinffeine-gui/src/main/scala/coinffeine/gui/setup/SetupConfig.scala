package coinffeine.gui.setup

import scalafx.beans.property.{ObjectProperty, StringProperty}

import coinffeine.peer.payment.okpay.{OkPayWalletAccess, OkPayCredentials}

class SetupConfig {

  val password: StringProperty = new StringProperty(this, "password")

  val okPayCredentials = new ObjectProperty[OkPayCredentials](this, "okPayCredentials")

  val okPayWalletAccess = new ObjectProperty[Option[OkPayWalletAccess]](
    this, "okPayWalletAccess", None)
}
