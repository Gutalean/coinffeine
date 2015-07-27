package coinffeine.gui.beans

import scalafx.beans.property.{ObjectProperty, BooleanProperty}

import coinffeine.common.test.UnitTest
import coinffeine.gui.beans.Implicits._

class ObservableBooleanPimpTest extends UnitTest {

  "Observable boolean pimp" should "convert into boolean binding" in {
    val p1: ObjectProperty[Boolean] = new ObjectProperty(this, "p1", true)
    val p2: BooleanProperty = new BooleanProperty(this, "p2")
    p2 <== p1.delegate.toBool

    p2.value shouldBe true
    p1.set(false)
    p2.value shouldBe false
  }
}
