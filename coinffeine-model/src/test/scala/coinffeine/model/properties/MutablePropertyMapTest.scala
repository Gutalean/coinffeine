package coinffeine.model.properties

import scala.concurrent.Promise
import scala.concurrent.duration._

import coinffeine.common.test.{FutureMatchers, UnitTest}

class MutablePropertyMapTest extends UnitTest with FutureMatchers {

  import scala.concurrent.ExecutionContext.Implicits._

  "A property map" should "fail to get a non existing entry" in {
    val prop = new MutablePropertyMap[String, Int]()
    an [NoSuchElementException] should be thrownBy { prop("foo") }
    prop.get("foo") should not be 'defined
  }

  it should "get and set an entry" in {
    val prop = new MutablePropertyMap[String, Int]()
    prop.set("foo", 1234)
    prop("foo") should be (1234)
  }

  it should "notify of preexisting values when adding a handler" in {
    val prop = new MutablePropertyMap[String, Int]()
    prop.set("foo", 1234)
    val notification = Promise[(String, Option[Int], Int)]()
    prop.onChange { (key, oldValue, newValue) =>
      notification.success((key, oldValue, newValue))
    }
    notification.future.futureValue shouldBe(("foo", None, 1234))
  }

  it should "invoke its handlers when entry is set" in new HandlerFixture {
    prop.set("foobar", fooInitialValue + 10)
    newValue.future.futureValue should be (fooInitialValue + 10)
  }

  it should "not invoke its handlers when set is not changing its value" in new HandlerFixture {
    prop.set("foobar", fooInitialValue)
    after(100.millis) {
      newValue shouldBe 'completed
      modifiedValue should not be 'completed
    }
  }

  it should "honour handler cancellation" in new HandlerFixture {
    handler.cancel()
    prop.set("foobar", fooInitialValue + 10)
    after(100.millis) {
      newValue should not be 'completed
    }
  }

  trait HandlerFixture {
    val fooInitialValue = 1234
    val prop = new MutablePropertyMap[String, Int]()
    val newValue = Promise[Int]()
    val modifiedValue = Promise[Int]()
    val handler = prop.onNewValue { (key, value) =>
      key should be ("foobar")
      if (newValue.isCompleted) modifiedValue.success(value)
      else newValue.success(value)
    }
  }

  private def after(time: FiniteDuration)(body: => Unit): Unit = {
    Thread.sleep(time.toMillis)
    body
  }
}
