package coinffeine.gui.setup

import scalafx.beans.property.{ObjectProperty, StringProperty}

import coinffeine.peer.payment.okpay.{OkPayWalletAccess, OkPayCredentials}

class SetupConfig {

  val password: StringProperty = new StringProperty(this, "password")

  val okPayCredentials: ObjectProperty[OkPayCredentials] =
    new ObjectProperty(this, "okPayCredentials")

  val okPayWalletAccess: ObjectProperty[OkPayWalletAccess] =
    new ObjectProperty[OkPayWalletAccess](this, "okPayWalletAccess")
}
