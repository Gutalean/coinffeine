package coinffeine.gui.beans

import javafx.beans.{InvalidationListener, Observable}
import scalafx.beans.property.{IntegerProperty, ObjectProperty}

import org.scalatest.concurrent.Eventually

import coinffeine.common.test.UnitTest

class ObservableBeanPropertyTest extends UnitTest with Eventually {

  "Observable bean property" should "invoke listener upon creation" in new Fixture {
    eventually { invalidationCounter shouldBe 1 }
  }

  it should "invoke listener upon bean is changed" in new Fixture {
    bean.value = Foobar(new IntegerProperty(this, "prop2", 20))
    eventually { invalidationCounter shouldBe 2 }
  }

  it should "invoke listener upon property is changed" in new Fixture {
    bean.value.prop.set(1234)
    eventually { invalidationCounter shouldBe 2 }
  }

  trait Fixture {
    case class Foobar(prop: IntegerProperty)

    val bean = new ObjectProperty(this, "bean", Foobar(new IntegerProperty(this, "prop", 7)))
    var invalidationCounter = 0
    val invalidate = new InvalidationListener {
      override def invalidated(observable: Observable) = invalidationCounter += 1
    }

    val observable = new ObservableBeanProperty[Foobar](bean, b => b.prop)
    observable.addListener(invalidate)
  }
}
