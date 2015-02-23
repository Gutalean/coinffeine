package coinffeine.peer.payment.okpay.blocking

import coinffeine.model.exchange.ExchangeId

/** Track the availability of blocked funds */
private class BlockedFundsAvailability {
  private var availability: Map[ExchangeId, Availability] = Map.empty
  private var announcedAvailability: Map[ExchangeId, Option[Availability]] = Map.empty

  def addFunds(funds: ExchangeId): Unit = {
    availability += funds -> Unavailable
    announcedAvailability += funds -> None
  }

  def removeFunds(funds: ExchangeId): Unit = {
    availability -= funds
    announcedAvailability -= funds
  }

  def setAvailable(funds: ExchangeId): Unit = {
    require(availability.contains(funds))
    availability += funds -> Available
  }

  def clearAvailable(): Unit = {
    availability = availability.map { case (k, _) => k -> Unavailable }
  }

  def areAvailable(funds: ExchangeId): Boolean = availability(funds) == Available

  /** Notify the changes since the last call to this method via callbacks */
  def notifyChanges(onAvailable: ExchangeId => Unit, onUnavailable: ExchangeId => Unit): Unit = {
    for (funds <- availability.keys) {
      (announcedAvailability(funds), availability(funds)) match {
        case (None | Some(Unavailable), Available) => onAvailable(funds)
        case (None | Some(Available), Unavailable) => onUnavailable(funds)
        case _ => // No change
      }
    }
    announcedAvailability = availability.map { case (k, v) => k -> Some(v) }
  }
}
