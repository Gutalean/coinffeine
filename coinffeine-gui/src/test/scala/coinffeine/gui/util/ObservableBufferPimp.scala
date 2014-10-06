package coinffeine.gui.util

import javafx.collections.FXCollections
import scalafx.collections.ObservableBuffer

import coinffeine.common.test.UnitTest
import coinffeine.gui.util.ScalafxImplicits._

class ObservableBufferPimp extends UnitTest {

  "An observable buffer" should "clear a list after binding" in {
    val buffer = new ObservableBuffer[Int]
    val other = FXCollections.observableArrayList[Int]
    other.addAll(1, 2, 3)
    buffer.bindToList(other)(identity[Int])
    other should be ('empty)
  }

  it should "propagate new elements to bounded" in new Fixture {
    buffer += 7
    other.get(0) should be (7)
    other.size should be (1)
  }

  it should "propagate multiple new elements to bounded" in new Fixture {
    buffer ++= Seq(7, 8, 9)
    other.get(0) should be (7)
    other.get(1) should be (8)
    other.get(2) should be (9)
    other.size should be (3)
  }

  it should "propagate removed elements to bounded" in new Fixture {
    buffer ++= Seq(7, 8, 9)
    buffer.remove(1)
    other.get(0) should be (7)
    other.get(1) should be (9)
    other.size should be (2)
  }

  it should "propagate clear to bounded" in new Fixture {
    buffer ++= Seq(7, 8, 9)
    buffer.clear()
    other should be ('empty)
  }

  it should "propagate updates to bounded" in new Fixture {
    buffer ++= Seq(7, 8, 9)
    buffer.update(1, 2)
    other.get(0) should be (7)
    other.get(1) should be (2)
    other.get(2) should be (9)
    other.size should be (3)
  }

  trait Fixture {
    val buffer = new ObservableBuffer[Int]
    val other = FXCollections.observableArrayList[Int]
    buffer.bindToList(other)(identity[Int])
  }
}
