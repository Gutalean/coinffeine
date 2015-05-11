package coinffeine.gui.beans

import scala.collection.JavaConversions._

import javafx.collections.ObservableList
import scalafx.collections.ObservableBuffer

class ObservableBufferPimp[A](buffer: ObservableBuffer[A]) {

  def bindToList[B](other: ObservableList[B])(f: A => B): Unit = {
    other.clear()
    other.addAll(buffer.map(f))
    buffer.onChange { (_, changes) =>
      changes.foreach {
        case ObservableBuffer.Add(pos, elems: Traversable[A]) =>
          other.addAll(pos, elems.map(f).toSeq)
        case ObservableBuffer.Remove(pos, elems: Traversable[A]) =>
          other.remove(pos, pos + elems.size)
        case ObservableBuffer.Reorder(start, end, perm) =>
          for (i <- start to (end - 1)) {
            val tmp = other.get(i)
            other.set(i, other.get(perm(i)))
            other.set(perm(i), tmp)
          }
        case ObservableBuffer.Update(start, end) =>
          for (i <- start to (end - 1)) {
            other.set(i, f(buffer.get(i)))
          }
      }
    }
  }
}
