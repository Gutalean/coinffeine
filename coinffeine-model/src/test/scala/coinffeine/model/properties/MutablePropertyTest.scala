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
    prop.onChange {
      case (_, newVal) => newValue.success(newVal)
    }
    prop.set(1234)
    newValue.future.futureValue should be (1234)
  }

  it should "not invoke its handlers when set is not changing its value" in {
    val prop = new MutableProperty(0)
    var newValue = Promise[Int]()
    prop.onChange {
      case (_, newVal) => newValue.success(newVal)
    }
    prop.set(0)
    after(100.millis) {
      newValue should not be ('completed)
    }
  }

  it should "honour handler cancellation" in {
    val prop = new MutableProperty(0)
    var newValue = Promise[Int]()
    val handler = prop.onChange {
      case (_, newVal) => newValue.success(newVal)
    }
    handler.cancel()
    prop.set(1234)
    after(100.millis) {
      newValue should not be ('completed)
    }
  }

  it should "honour when-handler" in {
    val prop = new MutableProperty(0)
    val cond = prop.when {
      case 100 => "success!"
    }
    cond should not be ('completed)
    prop.set(10)
    cond should not be ('completed)
    prop.set(100)
    cond.futureValue should be ("success!")
  }

  it should "honour when-handler if current value matches" in {
    val prop = new MutableProperty(0)
    val cond = prop.when {
      case 0 => "success!"
    }
    cond should be ('completed)
    cond.value.get.get should be ("success!")
  }

  private def after(time: FiniteDuration)(body: => Unit): Unit = {
    Thread.sleep(time.toMillis)
    body
  }
}
