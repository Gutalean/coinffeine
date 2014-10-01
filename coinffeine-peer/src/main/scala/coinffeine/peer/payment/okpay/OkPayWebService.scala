package coinffeine.peer.payment.okpay

import java.net.URI
import scalaxb.{DispatchHttpClientsAsync, Soap11ClientsAsync}

import coinffeine.peer.payment.okpay.generated.BasicHttpBinding_I_OkPayAPIBindings

/** SOAP client of the OKPay service
  *
  * FIXME: make this closeable
  *
  * @constructor
  * @param baseAddressOverride  Replace the endpoint specified at the WSDL when present
  */
class OkPayWebService(baseAddressOverride: Option[URI])
  extends BasicHttpBinding_I_OkPayAPIBindings
  with Soap11ClientsAsync
  with DispatchHttpClientsAsync {

  override val baseAddress: URI = baseAddressOverride.getOrElse(super.baseAddress)
}

object OkPayWebService {
  type Service = coinffeine.peer.payment.okpay.generated.I_OkPayAPI
}
