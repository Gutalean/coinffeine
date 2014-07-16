package coinffeine.peer.payment.okpay

import scalaxb.{DispatchHttpClientsAsync, Soap11ClientsAsync}

import coinffeine.peer.payment.okpay.generated._

object DefaultOKPayClient extends OKPayClient {

  trait Component extends OKPayClient.Component {
    override val okPayClient = DefaultOKPayClient
  }

  override def service: I_OkPayAPI = new BasicHttpBinding_I_OkPayAPIBindings with Soap11ClientsAsync
    with DispatchHttpClientsAsync {}.service
}
