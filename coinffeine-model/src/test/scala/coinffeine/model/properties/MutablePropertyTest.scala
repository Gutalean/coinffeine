package coinffeine.model.properties

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.Promise
import scala.concurrent.duration._

import org.scalatest.concurrent.Eventually

import coinffeine.common.test.{FutureMatchers, UnitTest}

class MutablePropertyTest extends UnitTest with Eventually with FutureMatchers {

  import scala.concurrent.ExecutionContext.Implicits._

  "A property" should "get its initial value" in {
    val prop = new MutableProperty(0)
    prop.get shouldBe 0
  }

  it should "get and set its value" in {
    val prop = new MutableProperty(0)
    prop.set(1234)
    prop.get shouldBe 1234
  }

  it should "invoke its handlers with the initial value when set" in {
    val prop = new MutableProperty(0)
    val newValue = Promise[Int]()
    prop.onNewValue(newValue.success(_))
    newValue.future.futureValue shouldBe 0
  }

  it should "invoke its handlers when set" in {
    val queue = new ConcurrentLinkedQueue[Int]()
    val prop = new MutableProperty(0)
    prop.onNewValue(v => queue.add(v))
    prop.set(1234)
    eventually {
      queue.toArray shouldBe Array(0, 1234)
    }
  }

  it should "not invoke its handlers when set is not changing its value" in {
    val queue = new ConcurrentLinkedQueue[Int]()
    val prop = new MutableProperty(0)
    prop.onNewValue(v => queue.add(v))
    prop.set(0)
    after(100.millis) {
      queue.toArray shouldBe Array(0)
    }
  }

  it should "honour handler cancellation" in {
    val queue = new ConcurrentLinkedQueue[Int]()
    val prop = new MutableProperty(0)
    val handler = prop.onNewValue(v => queue.add(v))
    handler.cancel()
    prop.set(1234)
    after(100.millis) {
      queue.toArray shouldBe Array(0)
    }
  }

  private def after(time: FiniteDuration)(body: => Unit): Unit = {
    Thread.sleep(time.toMillis)
    body
  }
}
