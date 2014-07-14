package com.coinffeine.common

import java.util.UUID

case class OrderId(value: String)

object OrderId {
  def random(): OrderId = OrderId(UUID.randomUUID().toString)
}
