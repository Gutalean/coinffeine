package coinffeine.peer.payment.okpay

import coinffeine.peer.payment.okpay.generated._

trait OKPayClient {

  def service: I_OkPayAPI
}

object OKPayClient {

  trait Component {

    def okPayClient: OKPayClient
  }
}
