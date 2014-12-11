package coinffeine.protocol.gateway.p2p

import scala.concurrent.Future

import net.tomp2p.futures.BaseFuture.FutureType
import net.tomp2p.futures.BaseFutureImpl

import coinffeine.common.test.UnitTest

class TomP2PFutureImplicitsTest extends UnitTest {

  /** Sample TomP2P future */
  class TestFuture extends BaseFutureImpl[TestFuture] {

    def succeed(): Unit = {
      lock.synchronized {
        if (!setCompletedAndNotify()) return
        `type` = FutureType.OK
      }
      notifyListerenrs()
    }

    def fail(message: String): Unit = {
      lock.synchronized {
        if (!setCompletedAndNotify()) return
        `type` = FutureType.FAILED
        reason = message
      }
      notifyListerenrs()
    }
  }

  "A TomP2P future converted to a scala one" should "finish when the underlying future finish" in
    new Fixture {
      convertedFuture should not be 'completed
      originalFuture.succeed()
      convertedFuture shouldBe 'completed
    }

  it should "succeed when the underlying future does it" in new Fixture {
    originalFuture.succeed()
    convertedFuture.futureValue shouldBe originalFuture
  }

  it should "fail when the underlying future does it" in new Fixture {
    originalFuture.fail("injected error")
    val ex = the [Exception] thrownBy convertedFuture.futureValue
    ex.getMessage should include("injected error")
  }

  private trait Fixture extends TomP2PFutureImplicits {
    val originalFuture = new TestFuture
    val convertedFuture: Future[TestFuture] = originalFuture
  }
}
