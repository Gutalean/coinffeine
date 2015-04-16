package coinffeine.gui.pane

import scalafx.beans.property.{ObjectProperty, StringProperty}
import scalafx.scene.Node
import scalafx.scene.control.{ScrollPane, Label}
import scalafx.scene.layout.{Pane, HBox, VBox}

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.scene.styles.NodeStyles

class PagePane(initialHeaderText: String = "",
               initialPageContent: Node = new Pane) extends VBox {

  styleClass += PagePane.StyleClass

  private val _headerText = new StringProperty(this, "headerText", initialHeaderText)
  def headerText = _headerText
  def headerText_=(text: String): Unit = { _headerText.value = text }

  private val _pageContent = new ObjectProperty[Node](this, "pageContent", initialPageContent)
  def pageContent = _pageContent
  def pageContent_=(content: Node): Unit = { _pageContent.value = content }

  private val header = new HBox {
    styleClass += "header"
    content = new Label { text <== _headerText }
  }

  _pageContent.delegate.bindToList(content) { pc =>
    val scroll = new ScrollPane() with NodeStyles.VExpand { content = pc }
    Seq(header, scroll)
  }
}

object PagePane {

  private val StyleClass = "page"
}
