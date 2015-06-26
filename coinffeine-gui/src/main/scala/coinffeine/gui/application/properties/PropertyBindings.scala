package coinffeine.gui.application.properties

import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}

import coinffeine.common.properties.Property
import coinffeine.gui.beans.Implicits._

trait PropertyBindings {

  def createBounded[A](prop: Property[A], name: String): ReadOnlyObjectProperty[A] = {
    val result = new ObjectProperty[A](this, name, prop.get)
    result.bind(prop.map(identity[A]))
    result
  }

}
