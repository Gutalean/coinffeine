package coinffeine.gui.control

import java.net.URI
import java.util.Properties
import scala.collection.JavaConverters._
import scalafx.Includes._
import scalafx.scene.layout.StackPane

import com.typesafe.scalalogging.LazyLogging

import coinffeine.gui.util.Browser

/** Support icon that links to external documentation in the browser.
  *
  * @constructor
  * @param helpId  Help topic identifier. It should match with a topic listed in
  *                {{{SupportWidget.LinkResource}}} or the icon won't be visible.
  */
class SupportWidget(helpId: String) extends StackPane with LazyLogging {

  SupportWidget.Links.get(helpId).fold(warnOnMissingLink())(showSupportIcon)

  private def showSupportIcon(link: URI): Unit = {
    content = new GlyphLabel {
      styleClass += "support-icon"
      icon = GlyphIcon.Support
      onMouseClicked = () => {
        Browser.default.browse(link)
      }
    }
  }

  private def warnOnMissingLink(): Unit = {
    logger.warn("No support link for '{}'", helpId)
  }
}

object SupportWidget {
  val LinksResource = "/help-links.properties"

  val Links: Map[String, URI] = {
    val javaProperties = new Properties()
    javaProperties.load(getClass.getResourceAsStream(LinksResource))
    for {
      key <- javaProperties.stringPropertyNames().asScala
      value = javaProperties.getProperty(key)
    } yield {
      key -> new URI(value)
    }
  }.toMap
}
