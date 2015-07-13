package coinffeine.peer.payment.okpay

import java.util.concurrent.atomic.AtomicReference

import coinffeine.peer.payment.okpay.ws.{OkPayWebService, OkPayWebServiceClient}

/** Factory of OKPay clients.
  *
  * Web-service machinery is shared among clients for efficiency and, configuration is
  * re-read every time to consider the latest settings.
  */
class OkPayClientFactory(lookupSettings: () => OkPaySettings)
  extends OkPayProcessorActor.ClientFactory {

  private val okPay = new OkPayWebService(lookupSettings().serverEndpointOverride)
  private val tokenCache = new AtomicReference[Option[String]](None)

  override def build(): OkPayClient = {
    val settings = lookupSettings()
    settings.apiCredentials.fold[OkPayClient](NotConfiguredClient) { credentials =>
      new OkPayWebServiceClient(
        okPay.service,
        tokenCache,
        credentials.walletId,
        credentials.seedToken,
        settings.periodicLimits
      )
    }
  }

  override def shutdown(): Unit = {
    okPay.httpClient.shutdown()
  }
}
