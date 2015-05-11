package coinffeine.gui.control

import javafx.beans.property.BooleanPropertyBase
import scalafx.Includes._
import scalafx.css.PseudoClass
import scalafx.scene.layout.StackPane

class StatusDisc extends StackPane {

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

  private val disc = new GlyphLabel {
    styleClass += "disc"
    icon <== when(failure) choose (GlyphIcon.Cross: GlyphIcon) otherwise (GlyphIcon.Mark: GlyphIcon)
  }

  children = Seq(disc)
}

object StatusDisc {

  val Failure = PseudoClass("failure")
}
