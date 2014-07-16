package coinffeine.peer.payment.okpay

import scalaxb.{DispatchHttpClientsAsync, Soap11ClientsAsync}

import coinffeine.peer.payment.okpay.generated._

class OKPayClient extends BasicHttpBinding_I_OkPayAPIBindings
  with Soap11ClientsAsync
  with DispatchHttpClientsAsync
