package coinffeine.gui.beans

import scalafx.beans.property.{IntegerProperty, ObjectProperty}

import coinffeine.common.test.UnitTest
import coinffeine.gui.beans.Implicits._

class ObservableIntegerPimpTest extends UnitTest {

  "Observable int pimp" should "convert into int binding" in {
    val p1: ObjectProperty[Int] = new ObjectProperty(this, "p1", 7)
    val p2: IntegerProperty = new IntegerProperty(this, "p2")
    p2 <== p1.delegate.toInt

    p2.value shouldBe 7
    p1.set(12)
    p2.value shouldBe 12
  }
}
