package coinffeine.model.properties

import scala.concurrent.Promise
import scala.concurrent.duration._

import coinffeine.common.test.{FutureMatchers, UnitTest}

class MutablePropertyTest extends UnitTest with FutureMatchers {

  import scala.concurrent.ExecutionContext.Implicits._


  "A property" should "get its initial value" in {
    val prop = new MutableProperty(0)
    prop.get should be (0)
  }

  it should "get and set its value" in {
    val prop = new MutableProperty(0)
    prop.set(1234)
    prop.get should be (1234)
  }

  it should "invoke its handlers when set" in {
    val prop = new MutableProperty(0)
    val newValue = Promise[Int]()
    prop.onNewValue(newValue.success(_))
    prop.set(1234)
    newValue.future.futureValue should be (1234)
  }

  it should "not invoke its handlers when set is not changing its value" in {
    val prop = new MutableProperty(0)
    val newValue = Promise[Int]()
    prop.onNewValue(newValue.success(_))
    prop.set(0)
    after(100.millis) {
      newValue should not be ('completed)
    }
  }

  it should "honour handler cancellation" in {
    val prop = new MutableProperty(0)
    val newValue = Promise[Int]()
    val handler = prop.onNewValue(newValue.success(_))
    handler.cancel()
    prop.set(1234)
    after(100.millis) {
      newValue should not be ('completed)
    }
  }

  private def after(time: FiniteDuration)(body: => Unit): Unit = {
    Thread.sleep(time.toMillis)
    body
  }
}
