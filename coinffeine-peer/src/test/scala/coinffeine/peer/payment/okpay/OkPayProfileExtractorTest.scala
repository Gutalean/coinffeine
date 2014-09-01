package coinffeine.peer.payment.okpay

import java.io.File

import com.typesafe.config.ConfigFactory

import coinffeine.common.test.{FutureMatchers, UnitTest}
import scala.concurrent.duration._

class OkPayProfileExtractorTest extends UnitTest with FutureMatchers {

  "The OkPay profile extractor" must "retrieve the walletId and token" in new Fixture {

    val profile = instance.configureProfile()
    val result = profile.futureValue(timeout)
    result should be('defined)
    result.get.walletId should be("OK734039871")
  }

  trait Fixture {
    val timeout = 2.minute
    val okPayCredentialsProperty = "OKPAY_CREDENTIALS"

    val (id, password) = loadCredentials()
    val instance = new OkPayProfileExtractor(id, password)

    def loadCredentials(): (String, String) = {
      Option(System.getenv(okPayCredentialsProperty))
        .fold(cancelOnMissingFile())(loadCredentialsFromFile)
    }

    private def loadCredentialsFromFile(filename: String): (String, String) = {
      val config = ConfigFactory.parseFile(new File(filename))
      (config.getString("id"), config.getString("password"))
    }

    private def cancelOnMissingFile(): Nothing = {
      cancel("Configuration file for OKPay integration test is undefined. " +
        s"Please configure -D$okPayCredentialsProperty=<file> parameter")
    }
  }
}
