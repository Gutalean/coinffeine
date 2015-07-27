package coinffeine.gui.beans

import scalafx.beans.property.{ObjectProperty, StringProperty}

import coinffeine.common.test.UnitTest
import coinffeine.gui.beans.Implicits._

class ObservableStringPimpTest  extends UnitTest {

  "Observable string pimp" should "convert into string binding" in {
    val p1: ObjectProperty[String] = new ObjectProperty(this, "p1", "foo")
    val p2: StringProperty = new StringProperty(this, "p2")
    p2 <== p1.delegate.toStr

    p2.value shouldBe "foo"
    p1.set("bar")
    p2.value shouldBe "bar"
  }
}
