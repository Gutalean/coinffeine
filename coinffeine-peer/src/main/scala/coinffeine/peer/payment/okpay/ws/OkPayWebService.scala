package coinffeine.peer.payment.okpay.ws

import java.net.URI
import scalaxb.{HttpClientsAsync, Soap11ClientsAsync}

import dispatch._
import org.slf4j.LoggerFactory

import coinffeine.peer.net.DaemonHttpClient
import coinffeine.peer.payment.okpay.generated.BasicHttpBinding_I_OkPayAPIBindings

/** SOAP client of the OKPay service
  *
  * @constructor
  * @param baseAddressOverride  Replace the endpoint specified at the WSDL when present
  */
class OkPayWebService(baseAddressOverride: Option[URI])
  extends BasicHttpBinding_I_OkPayAPIBindings
  with Soap11ClientsAsync
  with HttpClientsAsync {

  override val baseAddress: URI = baseAddressOverride.getOrElse(super.baseAddress)

  override lazy val httpClient = new AsyncHttpClient

  class AsyncHttpClient extends HttpClient {
    private val daemonHttpClient = new DaemonHttpClient()
    val http = new Http(daemonHttpClient.client)

    override def request(in: String, address: URI, headers: Map[String, String]): Future[String] = {
      val req = url(address.toString).setBodyEncoding("UTF-8") <:< headers << in
      http(req > as.String)
    }

    def shutdown(): Unit = {
      OkPayWebService.Log.info("Shutting down OKPay WS client...")
      http.shutdown()
      daemonHttpClient.shutdown()
    }
  }
}

object OkPayWebService {
  type Service = coinffeine.peer.payment.okpay.generated.I_OkPayAPI
  private val Log = LoggerFactory.getLogger(classOf[OkPayWebService])
}
