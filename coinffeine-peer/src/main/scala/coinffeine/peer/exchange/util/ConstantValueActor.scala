package coinffeine.peer.exchange.util

import akka.actor.Actor

class ConstantValueActor extends Actor {
  import coinffeine.peer.exchange.util.ConstantValueActor._

  var response: Option[Any] = None
  override val receive: Receive = {
    case SetValue(v) => response = Some(v)
    case UnsetValue => response = None
    case _ => response.map(sender().!)
  }
}

/** Control messages for ConstantValueActor */
object ConstantValueActor {
  case class SetValue(v: Any)
  case object UnsetValue
}
