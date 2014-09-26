package coinffeine.peer.market.orders.funds

import akka.actor._

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market.RequiredFunds
import coinffeine.peer.market.orders.OrderActor
import coinffeine.peer.market.orders.controller.FundsBlocker

/** Manages order funds by delegating on an [[FundsBlockerActor]].
  *
  * To do so, this class should be instantiated within an [[Actor]] and including
  * [[DelegatedFundsBlocker#blockingFunds]] in its receive behavior.
  */
class DelegatedFundsBlocker(
     fundsBlockingProps: RequiredFunds[_ <: FiatCurrency] => Props)(implicit context: ActorContext)
  extends OrderActor.OrderFundsBlocker {

  private case class BlockingInProgress(ref: ActorRef, listener: FundsBlocker.Listener)

  private implicit val self = context.self
  private var tasks = Set.empty[BlockingInProgress]

  override def blockFunds(funds: RequiredFunds[_ <: FiatCurrency],
                          listener: FundsBlocker.Listener): Unit = {
    val ref = context.actorOf(fundsBlockingProps(funds))
    tasks += BlockingInProgress(ref, listener)
  }

  val blockingFunds: Actor.Receive = {
    case FundsBlockerActor.BlockingResult(result) =>
      tasks.find(_.ref == context.sender()).foreach { task =>
        task.listener.onComplete(result)
        tasks -= task
      }
  }
}
