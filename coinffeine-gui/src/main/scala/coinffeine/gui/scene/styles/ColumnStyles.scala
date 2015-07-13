package coinffeine.gui.scene.styles

import scalafx.geometry.HPos
import scalafx.scene.layout.{ColumnConstraints, Priority}

/** Styles applicable to columns. */
object ColumnStyles {

  trait FieldTitle { this: ColumnConstraints =>
    halignment = HPos.Right
    fillWidth = false
    hgrow = Priority.Never
  }

  trait FieldValue { this: ColumnConstraints =>
    fillWidth = true
    hgrow = Priority.Always
  }
}
