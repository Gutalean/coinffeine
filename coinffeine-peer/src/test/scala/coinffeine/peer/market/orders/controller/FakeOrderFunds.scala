package coinffeine.peer.market.orders.controller

import coinffeine.model.exchange.Exchange

class FakeOrderFunds extends OrderFunds {

  private var funds: Option[Exchange.BlockedFunds] = None
  private var available: Boolean = false
  private var released: Boolean = false

  override def areBlocked = funds.isDefined
  override def get = funds.get
  override def areAvailable = available

  override def release(): Unit = {
    requireNotReleased()
    funds = None
    available = false
    released = true
  }

  def blockFunds(ids: Exchange.BlockedFunds): Unit = {
    funds = Some(ids)
  }

  def makeAvailable(): Unit = {
    requireNotReleased()
    require(areBlocked && !available)
    available = true
    listeners.foreach(_.onFundsAvailable(this))
  }

  def makeUnavailable(): Unit = {
    requireNotReleased()
    require(areBlocked && available)
    available = false
    listeners.foreach(_.onFundsUnavailable(this))
  }

  def areReleased: Boolean = released

  private def requireNotReleased(): Unit = {
    require(!released, "Funds already released")
  }
}
