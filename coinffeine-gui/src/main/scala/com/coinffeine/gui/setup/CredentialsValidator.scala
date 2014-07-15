package com.coinffeine.gui.setup

import scala.concurrent.Future

import com.coinffeine.common.paymentprocessor.okpay.OkPayCredentials

/** Test OKPay credentials by contacting the service */
trait CredentialsValidator {
  def apply(credentials: OkPayCredentials): Future[CredentialsValidator.Result]
}

object CredentialsValidator {
  sealed trait Result
  case object ValidCredentials extends Result
  case class InvalidCredentials(cause: String) extends Result
}

