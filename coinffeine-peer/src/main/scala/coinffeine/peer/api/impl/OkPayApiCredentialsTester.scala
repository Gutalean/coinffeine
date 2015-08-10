package coinffeine.peer.api.impl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

import coinffeine.common.ScalaFutureImplicits._
import coinffeine.model.currency.FiatAmounts
import coinffeine.peer.api.CoinffeinePaymentProcessor.TestResult
import coinffeine.peer.payment.okpay.OkPayClient.{AuthenticationFailed, ClientNotFound}
import coinffeine.peer.payment.okpay.{OkPayApiCredentials, OkPayClientFactory, OkPaySettings}

class OkPayApiCredentialsTester(lookupSettings: () => OkPaySettings) {

  def test(credentials: OkPayApiCredentials): Future[TestResult] = {
    def patchedSettings() = {
      lookupSettings().withApiCredentials(credentials)
    }

    val factory = new OkPayClientFactory(patchedSettings)
    val result = testRequest(factory)
    result.onComplete { case _ => factory.shutdown() }
    classifyOutcome(result)
  }

  private def testRequest(factory: OkPayClientFactory): Future[FiatAmounts] =
    factory.build().currentBalances()

  private def classifyOutcome(outcome: Future[FiatAmounts]): Future[TestResult] =
    outcome.materialize.map {
      case Success(_) => TestResult.Valid
      case Failure(ClientNotFound(_, _) | AuthenticationFailed(_, _)) => TestResult.Invalid
      case Failure(_) => TestResult.CannotConnect
    }
}
