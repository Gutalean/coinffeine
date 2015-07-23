package coinffeine.peer.exchange.handshake

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future

import akka.actor.ActorContext

class StubAccountIdValidator extends AccountIdValidator {

  private val result = new AtomicReference[AccountIdValidator.Result](null)

  def givenAccountIdWillBeValid(): Unit = {
    result.set(AccountIdValidator.Valid)
  }

  def givenAccountIdWillBeInvalid(): Unit = {
    result.set(AccountIdValidator.Invalid)
  }

  override def validate(accountId: String)(implicit context: ActorContext) =
    Future.successful(result.get())
}
