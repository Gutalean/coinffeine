package coinffeine.gui.beans

import javafx.beans.value.{ObservableValue, ObservableValueBase}
import javafx.beans.{InvalidationListener, Observable}

/** Observable value combiner.
  *
  * This class provides a combination of two observable values in another
  * `ObservableValue` instance which value depends on a combiner function.
  */
@deprecated("Use zip() function provided by ObservableValuePimp instead")
class ObservableValueCombiner[A, B, C](a: ObservableValue[A],
                                       b: ObservableValue[B],
                                       combiner: (A, B) => C) extends ObservableValueBase[C] {

  private val invalidate = new InvalidationListener {
    override def invalidated(observable: Observable) = fireValueChangedEvent()
  }


  override def getValue = combiner(a.getValue, b.getValue)

  a.addListener(invalidate)
  b.addListener(invalidate)
}

object ObservableValueCombiner {

  @deprecated("Use zip() function provided by ObservableValuePimp instead")
  def apply[A, B, C](a: ObservableValue[A],
                     b: ObservableValue[B])
                    (combiner: (A, B) => C): ObservableValueCombiner[A, B, C] =
    new ObservableValueCombiner(a, b, combiner)
}
