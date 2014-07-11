package com.coinffeine.gui.application

import scalafx.collections.ObservableBuffer

import com.coinffeine.client.api.{CoinffeineApp, EventHandler}
import com.coinffeine.gui.application.properties.OrderProperties
import com.coinffeine.gui.util.FxEventHandler

class ApplicationProperties(app: CoinffeineApp) {

  val ordersProperty = ObservableBuffer[OrderProperties]()

  private val eventHandler: EventHandler = FxEventHandler {
    case CoinffeineApp.OrderSubmittedEvent(order) =>
      ordersProperty.add(OrderProperties(order))
    case CoinffeineApp.OrderCancelledEvent(order) =>
      ordersProperty.remove(order.id)
  }

  app.observe(eventHandler)
}
