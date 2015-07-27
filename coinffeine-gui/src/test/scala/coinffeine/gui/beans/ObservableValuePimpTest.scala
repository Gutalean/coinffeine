package coinffeine.gui.beans

import scalafx.beans.property._
import scalafx.collections.ObservableBuffer

import coinffeine.common.test.UnitTest
import coinffeine.gui.beans.Implicits._

class ObservableValuePimpTest extends UnitTest {

  "Observable value pimp" must "map as object binding" in {
    val p1: ObjectProperty[String] = new ObjectProperty(this, "p1", "Hello World")
    val p2: ObjectProperty[String] = new ObjectProperty(this, "p2")
    p2 <== p1.delegate.map(_ + "!")

    p2.value shouldBe "Hello World!"
    p1.set("Attack")
    p2.value shouldBe "Attack!"
  }

  it must "map as string binding" in {
    val p1: ObjectProperty[Int] = new ObjectProperty(this, "p1", 9)
    val p2: StringProperty = new StringProperty(this, "p2")
    p2 <== p1.delegate.mapToString(n => s"You have $n")

    p2.value shouldBe "You have 9"
    p1.set(0)
    p2.value shouldBe "You have 0"
  }

  it must "map as int binding" in {
    val p1: ObjectProperty[String] = new ObjectProperty(this, "p1", "Hello World")
    val p2: IntegerProperty = new IntegerProperty(this, "p2")
    p2 <== p1.delegate.mapToInt(_.length)

    p2.value shouldBe 11
    p1.set("")
    p2.value shouldBe 0
  }

  it must "bind to list" in {
    val p1: ObjectProperty[String] = new ObjectProperty(this, "p1", "Hello World")
    val list: ObservableBuffer[String] = new ObservableBuffer()
    p1.delegate.bindToList(list)(_.split(' '))

    list.toArray shouldBe Array("Hello", "World")
    p1.set("This is SPARTA")
    list.toArray shouldBe Array("This", "is", "SPARTA")
  }

  it must "flatMap as object binding" in {
    class Foobar(val prop: ObjectProperty[String]) {
      def this(s: String) = this(new ObjectProperty(this, "prop", s))
    }

    val p1: ObjectProperty[Foobar] = new ObjectProperty(this, "p1", new Foobar("Hello World"))
    val p2: ObjectProperty[String] = new ObjectProperty(this, "p2")
    p2 <== p1.delegate.flatMap(_.prop)

    p2.value shouldBe "Hello World"

    // Must propagate inner value change...
    p1.value.prop.set("This is SPARTA")
    p2.value shouldBe "This is SPARTA"

    // ...and also bean replacement
    p1.set(new Foobar("That is MOSTOLES"))
    p2.value shouldBe "That is MOSTOLES"
  }
}
