package coinffeine.gui.beans

import scalafx.beans.property.{DoubleProperty, ObjectProperty}

import coinffeine.common.test.UnitTest
import coinffeine.gui.beans.Implicits._

class ObservableDoublePimpTest extends UnitTest {

  "Observable double pimp" should "convert into double binding" in {
    val p1: ObjectProperty[Double] = new ObjectProperty(this, "p1", 3.14)
    val p2: DoubleProperty = new DoubleProperty(this, "p2")
    p2 <== p1.delegate.toDouble

    p2.value shouldBe 3.14
    p1.set(1.23)
    p2.value shouldBe 1.23
  }
}
