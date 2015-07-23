package coinffeine.peer.exchange.handshake

import scala.concurrent.Future

import akka.actor.ActorContext

trait AccountIdValidator {

  /** Check if a given string is a valid account id to operate with */
  def validate(accountId: String)
              (implicit context: ActorContext): Future[AccountIdValidator.Result]
}

object AccountIdValidator {
  sealed trait Result
  case object Valid extends Result
  case object Invalid extends Result
}
