package com.coinffeine.gui.application


import scalafx.collections.ObservableBuffer

import coinffeine.model.market.OrderId
import coinffeine.peer.api.{CoinffeineApp, EventHandler}
import com.coinffeine.gui.application.properties.OrderProperties
import com.coinffeine.gui.util.FxEventHandler

class ApplicationProperties(app: CoinffeineApp) {

  val ordersProperty = ObservableBuffer[OrderProperties]()

  private val eventHandler: EventHandler = FxEventHandler {
    case CoinffeineApp.OrderSubmittedEvent(order) =>
      require(!orderExist(order.id), s"Duplicated OrderId: ${order.id}")
      ordersProperty.add(OrderProperties(order))
    case CoinffeineApp.OrderCancelledEvent(orderId) =>
      ordersProperty.zipWithIndex.foreach { t =>
        if(t._1.order.id == orderId) {
          ordersProperty.remove(t._2)
        }
      }
  }

  private def orderExist(orderId: OrderId): Boolean = ordersProperty.exists(_.order.id == orderId)

  app.observe(eventHandler)
}
