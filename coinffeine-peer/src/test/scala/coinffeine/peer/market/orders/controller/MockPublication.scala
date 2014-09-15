package coinffeine.peer.market.orders.controller

import org.scalatest.Assertions

import coinffeine.model.currency.FiatCurrency
import coinffeine.peer.market.orders.controller.OrderPublication.Listener

class MockPublication[C <: FiatCurrency] extends OrderPublication[C] with Assertions {
  private var listeners = Seq.empty[OrderPublication.Listener]
  private var _inMarket = false
  private var pendingPublication = false

  override def addListener(listener: Listener): Unit = {
    listeners :+= listener
  }

  override def isInMarket: Boolean = _inMarket

  override def keepPublishing(): Unit = {
    if (!_inMarket) {
      pendingPublication = true
    }
  }

  def expectSuccessfulPublication(): Unit = {
    if (!pendingPublication) {
      fail("Not trying to publish")
    }
    _inMarket = true
    listeners.foreach(_.inMarket())
    pendingPublication = false
  }

  def expectUnsuccessfulPublication(): Unit = {
    if (!pendingPublication) {
      fail("Not trying to publish")
    }
    listeners.foreach(_.offline())
  }

  override def stopPublishing(): Unit = {
    pendingPublication = false
    if (_inMarket) {
      _inMarket = false
      listeners.foreach(_.offline())
    }
  }
}
