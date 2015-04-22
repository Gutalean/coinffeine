package coinffeine.gui.beans

import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.beans.{InvalidationListener, Observable}

/** This class provides the means to observe a property of a bean.
  *
  * Let's say we have the following scenario: a property that holds a bean that in turn holds
  * another property. This class is useful to observe that inner property of that bean. If the
  * property holding the bean changes, this observable will be invalidated. If the bean does not
  * change but the inner property does, it is invalidated as well.
  *
  * @param bean               The bean which property is gonna be observed
  * @param propertyExtractor  The function that extracts the property from the bean
  */
class ObservableBeanProperty[A](bean: ObservableValue[A],
                                propertyExtractor: A => ObservableValue[_]) extends Observable {
  
  private var property: Option[ObservableValue[_]] = None
  private var listeners: Set[InvalidationListener] = Set.empty
  
  private val propertyListener = new InvalidationListener {
    override def invalidated(observable: Observable) = {
      // We must get property value to clean up its invalidation state
      property.foreach(_.getValue)

      invokeListeners()
    }
  }

  private def invokeListeners(): Unit = listeners.foreach(_.invalidated(this))

  private def replacePropertyListeners(newValue: A): Unit = {
    val newProperty = propertyExtractor(newValue)
    newProperty.addListener(propertyListener)
    property.foreach(_.removeListener(propertyListener))
    property = Some(newProperty)
    invokeListeners()
  }
  
  override def addListener(listener: InvalidationListener) = {
    listeners += listener
    listener.invalidated(this)
  }

  override def removeListener(listener: InvalidationListener) = listeners -= listener

  bean.addListener(new ChangeListener[A] {
    override def changed(observable: ObservableValue[_ <: A], oldValue: A, newValue: A) = {
      replacePropertyListeners(newValue)
    }
  })

  replacePropertyListeners(bean.getValue)
}
