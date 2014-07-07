package com.coinffeine.common.paymentprocessor.okpay

import scalaxb.{DispatchHttpClientsAsync, Soap11ClientsAsync}

import com.coinffeine.common.paymentprocessor.okpay.generated._

object DefaultOKPayClient extends OKPayClient {

  trait Component extends OKPayClient.Component {
    override val okPayClient = DefaultOKPayClient
  }

  override def service: I_OkPayAPI = new BasicHttpBinding_I_OkPayAPIBindings with Soap11ClientsAsync
    with DispatchHttpClientsAsync {}.service
}
