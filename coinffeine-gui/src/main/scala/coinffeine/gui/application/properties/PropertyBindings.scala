package coinffeine.gui.application.properties

import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}

import coinffeine.common.properties.{PropertyMap, Property}
import coinffeine.gui.beans.Implicits._

trait PropertyBindings {

  def createBounded[A](prop: Property[A], name: String): ReadOnlyObjectProperty[A] = {
    val result = new ObjectProperty[A](this, name, prop.get)
    result.bind(prop.map(identity[A]))
    result
  }

  def createBoundedToMapEntry[K, A, B](prop: PropertyMap[K, A], name: String, key: K)
                                      (f: A => B): ReadOnlyObjectProperty[Option[B]] = {
    val result = new ObjectProperty[Option[B]](this, name, prop.get(key).map(f))
    result.bind(prop.mapEntry(key)(f))
    result
  }
}
