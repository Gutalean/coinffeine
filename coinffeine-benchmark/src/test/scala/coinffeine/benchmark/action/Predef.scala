package coinffeine.benchmark.action

import io.gatling.core.session._

import coinffeine.protocol.messages.PublicMessage

object Predef {

  case class AskWord(requestName: Expression[String]) {
    def message(msg: PublicMessage) = AskRequestBuilder(requestName, msg)
  }

  def ask(requestName: Expression[String]) = new AskWord(requestName)

  def putOrders(requestName: Expression[String]) = PutPeerPositionsBuilder(requestName)
}
