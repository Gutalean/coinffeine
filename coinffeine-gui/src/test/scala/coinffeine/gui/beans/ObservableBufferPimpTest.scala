package coinffeine.gui.beans

import javafx.collections.FXCollections
import scalafx.collections.ObservableBuffer

import coinffeine.common.test.UnitTest
import coinffeine.gui.beans.Implicits._

class ObservableBufferPimpTest extends UnitTest {

  "An observable buffer" should "clear a list after binding" in {
    val buffer = new ObservableBuffer[Int]
    val other = FXCollections.observableArrayList[Int]
    other.addAll(1, 2, 3)
    buffer.bindToList(other)(identity[Int])
    other shouldBe 'empty
  }

  it should "propagate elements existing before the binding" in {
    val buffer = new ObservableBuffer[Int]
    buffer.addAll(1)
    val other = FXCollections.observableArrayList[Int]
    buffer.bindToList(other)(identity[Int])
    other.get(0) shouldBe 1
  }

  it should "propagate new elements to bounded" in new Fixture {
    buffer += 7
    other.get(0) shouldBe 7
    other.size shouldBe 1
  }

  it should "propagate multiple new elements to bounded" in new Fixture {
    buffer ++= Seq(7, 8, 9)
    other.get(0) shouldBe 7
    other.get(1) shouldBe 8
    other.get(2) shouldBe 9
    other.size shouldBe 3
  }

  it should "propagate removed elements to bounded" in new Fixture {
    buffer ++= Seq(7, 8, 9)
    buffer.remove(1)
    other.get(0) shouldBe 7
    other.get(1) shouldBe 9
    other.size shouldBe 2
  }

  it should "propagate clear to bounded" in new Fixture {
    buffer ++= Seq(7, 8, 9)
    buffer.clear()
    other shouldBe 'empty
  }

  it should "propagate updates to bounded" in new Fixture {
    buffer ++= Seq(7, 8, 9)
    buffer.update(1, 2)
    other.get(0) shouldBe 7
    other.get(1) shouldBe 2
    other.get(2) shouldBe 9
    other.size shouldBe 3
  }

  trait Fixture {
    val buffer = new ObservableBuffer[Int]
    val other = FXCollections.observableArrayList[Int]
    buffer.bindToList(other)(identity[Int])
  }
}
