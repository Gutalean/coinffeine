package coinffeine.model.properties

import scala.concurrent.duration._

import org.scalatest.concurrent.Eventually

import coinffeine.common.test.UnitTest

class MutablePropertyTest extends UnitTest with Eventually {

  import scala.concurrent.ExecutionContext.Implicits._

  val prop = new MutableProperty(0)

  "A property" should "get its initial value" in {
    prop.get should be (0)
  }

  it should "get and set its value" in {
    prop.set(1234)
    prop.get should be (1234)
  }

  it should "invoke its handlers when set" in {
    var newValue = 0
    prop.onChange {
      case (_, newVal) => newValue = newVal
    }
    prop.set(9876)
    eventually {
      newValue should be (9876)
    }
  }

  it should "not invoke its handlers when set is not changing its value" in {
    var newValue = 0
    prop.onChange {
      case (_, newVal) => newValue = newVal
    }
    prop.set(9876)
    after(100.millis) {
      newValue should be (0)
    }
  }

  private def after(time: FiniteDuration)(body: => Unit): Unit = {
    Thread.sleep(time.toMillis)
    body
  }
}
