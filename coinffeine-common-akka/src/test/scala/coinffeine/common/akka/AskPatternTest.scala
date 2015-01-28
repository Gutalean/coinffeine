package coinffeine.common.akka

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.testkit._
import akka.util.Timeout

import coinffeine.common.akka.test.AkkaSpec

class AskPatternTest extends AkkaSpec {

  import system.dispatcher

  case object Request
  case class Response(payload: String)
  case class Error(code: Int)

  val testTimeout = 200.millis.dilated
  implicit val timeout = Timeout(testTimeout)

  "The improved ask pattern" should "report timeouts" in new Fixture {
    val ex = expectFailure[RuntimeException](AskPattern(
      to = probe.ref,
      request = Request,
      errorMessage = "cannot perform request"
    ).withReply[Response])
    ex.getMessage should startWith regex "cannot perform request: timeout of .* waiting for response"
  }

  it should "return a response of the expected type" in new Fixture {
    val future = AskPattern(
      to = probe.ref,
      request = Request,
      errorMessage = "cannot perform request"
    ).withReply[Response]

    probe.expectMsg(Request)
    probe.reply(Response("OK"))

    future.futureValue(testTimeout * 2) shouldBe Response("OK")
  }

  it should "extract an error from a failed request" in new Fixture {
    val future = AskPattern(
      to = probe.ref,
      request = Request,
      errorMessage = "cannot perform request"
    ).withReplyOrError[Response, Error](error => new Exception("code " + error.code))

    probe.expectMsg(Request)
    probe.reply(Error(501))

    expectFailure[Exception](future).getCause.getMessage should include("code 501")
  }

  trait Fixture {
    val probe = TestProbe()

    def expectFailure[E: Manifest](f: Future[_]): E = {
      the [E] thrownBy {
        f.futureValue(testTimeout * 2)
      }
    }
  }
}
