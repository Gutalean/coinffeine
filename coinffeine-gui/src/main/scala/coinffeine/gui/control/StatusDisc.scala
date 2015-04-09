package coinffeine.gui.control

import javafx.beans.property.BooleanPropertyBase

import scalafx.css.PseudoClass
import scalafx.scene.shape.Circle

class StatusDisc extends Circle {

  styleClass += "status-disc"
  delegate.pseudoClassStateChanged(StatusDisc.Failure, false)

  val failure = new BooleanPropertyBase() {
    override def getName = "failure"
    override def getBean = StatusDisc.this
    override def invalidated() = {
      super.invalidated()
      delegate.pseudoClassStateChanged(StatusDisc.Failure, get())
    }
  }

  radius = 8
}

object StatusDisc {

  val Failure = PseudoClass("failure")
}
