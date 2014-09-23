package coinffeine.peer.market.orders

import akka.actor._

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Exchange
import coinffeine.model.exchange.Exchange.BlockedFunds
import coinffeine.model.market.RequiredFunds
import coinffeine.peer.market.orders.controller.OrderFunds

/** Manages order funds by delegating on an OrderFundsActor.
  *
  * To do so, this class should be instantiated within an [[Actor]] and including
  * [[DelegatedOrderFunds#managingFundsAvailability]] in its receive behavior.
  */
class DelegatedOrderFunds(orderFundsProps: Props, requiredFunds: RequiredFunds[_ <: FiatCurrency])
                         (implicit context: ActorContext) extends OrderFunds {

  private implicit val self = context.self

  private sealed trait FundsAvailability {
    def areBlocked: Boolean
    def areAvailable: Boolean
    def get: Exchange.BlockedFunds
    def becomeAvailable(funds: Exchange.BlockedFunds): FundsAvailability
    def becomeUnavailable: FundsAvailability
  }

  private case object BlockingFunds extends FundsAvailability {
    override val areBlocked = false
    override val areAvailable = false
    override def get = throw new NoSuchElementException("Already blocking funds")
    override def becomeAvailable(funds: BlockedFunds) = AvailableFunds(funds)
    override def becomeUnavailable = this
  }

  private case class AvailableFunds(ids: Exchange.BlockedFunds) extends FundsAvailability {
    override val areBlocked = true
    override val areAvailable = true
    override def get = ids
    override def becomeAvailable(funds: BlockedFunds): FundsAvailability = {
      require(funds == ids, s"Blocked funds unexpectedly changed from $ids to $funds")
      this
    }
    override def becomeUnavailable: FundsAvailability = UnavailableFunds(ids)
  }

  private case class UnavailableFunds(ids: Exchange.BlockedFunds) extends FundsAvailability {
    override val areBlocked = true
    override val areAvailable = false
    override def get = ids
    override def becomeAvailable(funds: BlockedFunds): FundsAvailability = {
      require(funds == ids, s"Blocked funds unexpectedly changed from $ids to $funds")
      AvailableFunds(ids)
    }
    override def becomeUnavailable: FundsAvailability = this
  }

  private var funds: FundsAvailability = BlockingFunds
  private val delegate = spawnDelegate()

  override def areBlocked: Boolean = funds.areBlocked
  override def areAvailable: Boolean = funds.areAvailable
  override def get: Exchange.BlockedFunds = funds.get

  override def release(): Unit = {
    delegate ! OrderFundsActor.UnblockFunds
  }

  val managingFundsAvailability: Actor.Receive = {
    case OrderFundsActor.AvailableFunds(availableBlockedFunds) =>
      updateFunds(funds.becomeAvailable(availableBlockedFunds))
    case OrderFundsActor.UnavailableFunds =>
      updateFunds(funds.becomeUnavailable)
  }

  private def updateFunds(newFunds: FundsAvailability): Unit = {
    val wereAvailable = funds.areAvailable
    funds = newFunds
    (wereAvailable, funds.areAvailable) match {
      case (false, true) => listeners.foreach(_.onFundsAvailable(this))
      case (true, false) => listeners.foreach(_.onFundsUnavailable(this))
      case _ => // No change
    }
  }

  private def spawnDelegate(): ActorRef = {
    val ref = context.actorOf(orderFundsProps, "funds")
    ref ! OrderFundsActor.BlockFunds(requiredFunds)
    ref
  }
}
