package com.coinffeine.gui.application

import scalafx.collections.ObservableBuffer

import com.coinffeine.client.api.{CoinffeineApp, EventHandler}
import com.coinffeine.gui.application.properties.OrderProperties
import com.coinffeine.gui.util.FxEventHandler

class ApplicationProperties(app: CoinffeineApp) {

  val ordersProperty = ObservableBuffer[OrderProperties]()

  private val eventHandler: EventHandler = FxEventHandler {
    case CoinffeineApp.OrderSubmittedEvent(orderId) =>
      ordersProperty.add(OrderProperties(orderId))
    case CoinffeineApp.OrderCancelledEvent(orderId) =>
      ordersProperty.remove(orderId)
  }

  app.observe(eventHandler)
}
