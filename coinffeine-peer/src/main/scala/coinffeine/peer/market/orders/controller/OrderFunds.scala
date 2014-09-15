package coinffeine.peer.market.orders.controller

import coinffeine.model.exchange.Exchange
import coinffeine.model.exchange.Exchange.BlockedFunds

sealed trait OrderFunds {
 def get: Exchange.BlockedFunds
}

case object NoFunds extends OrderFunds {
  override def get: BlockedFunds = throw new NoSuchElementException("no funds")
}

case class AvailableFunds(ids: Exchange.BlockedFunds) extends OrderFunds {
  override def get: BlockedFunds = ids
}

case class UnavailableFunds(ids: Exchange.BlockedFunds) extends OrderFunds {
  override def get: BlockedFunds = ids
}
