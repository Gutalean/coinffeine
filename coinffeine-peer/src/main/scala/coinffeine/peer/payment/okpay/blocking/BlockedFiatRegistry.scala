package coinffeine.peer.payment.okpay.blocking

import scalaz.{Scalaz, Validation}

import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor

import coinffeine.model.currency.{CurrencyAmount, FiatAmount, FiatCurrency}
import coinffeine.model.exchange.ExchangeId
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.peer.payment.PaymentProcessorActor.FundsAvailabilityEvent

private[okpay] class BlockedFiatRegistry(override val persistenceId: String)
  extends PersistentActor with ActorLogging {

  import Scalaz._
  import BlockedFiatRegistry._

  private case class BlockedFundsInfo[C <: FiatCurrency](
      id: ExchangeId, remainingAmount: CurrencyAmount[C]) {

    def canUseFunds(amount: CurrencyAmount[C]): Boolean = amount <= remainingAmount
  }

  private val balances = new MultipleBalance()
  private var funds: Map[ExchangeId, BlockedFundsInfo[_ <: FiatCurrency]] = Map.empty
  private var arrivalOrder: Seq[ExchangeId] = Seq.empty
  private var backedFunds = Set.empty[ExchangeId]
  private var notBackedFunds = Set.empty[ExchangeId]
  private var neverBackedFunds = Set.empty[ExchangeId]

  override def receiveRecover: Receive = {
    case event: FundsBlockedEvent => onFundsBlocked(event)
    case event: FundsUsedEvent => onFundsUsed(event)
    case event: FundsUnblockedEvent => onFundsUnblocked(event)
  }

  override def receiveCommand: Receive = {
    case RetrieveTotalBlockedFunds(currency) =>
      totalBlockedForCurrency(currency) match {
        case Some(blockedFunds) => sender ! BlockedFiatRegistry.TotalBlockedFunds(blockedFunds)
        case None => sender ! BlockedFiatRegistry.TotalBlockedFunds(currency.Zero)
      }

    case BalancesUpdate(newBalances) =>
      balances.resetTo(newBalances)
      updateBackedFunds()

    case UseFunds(fundsId, amount) =>
      canUseFunds(fundsId, amount).fold(
        succ = funds => persist(FundsUsedEvent(fundsId, amount)) { event =>
          onFundsUsed(event)
          sender() ! FundsUsed(fundsId, amount)
        },
        fail = reason => sender() ! CannotUseFunds(fundsId, amount, reason)
      )

    case PaymentProcessorActor.BlockFunds(fundsId, _) if funds.contains(fundsId) =>
      sender() ! PaymentProcessorActor.AlreadyBlockedFunds(fundsId)

    case PaymentProcessorActor.BlockFunds(fundsId, amount) =>
      persist(FundsBlockedEvent(fundsId, amount)) { event =>
        sender() ! PaymentProcessorActor.BlockedFunds(fundsId)
        onFundsBlocked(event)
      }

    case PaymentProcessorActor.UnblockFunds(fundsId) =>
      persist(FundsUnblockedEvent(fundsId))(onFundsUnblocked)
  }

  private def onFundsBlocked(event: FundsBlockedEvent): Unit = {
    arrivalOrder :+= event.fundsId
    funds += event.fundsId -> BlockedFundsInfo(event.fundsId, event.amount)
    setNeverBacked(event.fundsId)
    updateBackedFunds()
  }

  private def onFundsUsed(event: FundsUsedEvent): Unit = {
    type C = event.amount.currency.type
    val fundsToUse = funds(event.fundsId).asInstanceOf[BlockedFundsInfo[C]]
    updateFunds(fundsToUse.copy(remainingAmount =
      fundsToUse.remainingAmount - event.amount.asInstanceOf[CurrencyAmount[C]]))
    balances.reduceBalance(event.amount)
    updateBackedFunds()
  }

  private def onFundsUnblocked(event: FundsUnblockedEvent): Unit = {
    arrivalOrder = arrivalOrder.filter(_ != event.fundsId)
    funds -= event.fundsId
    clearBacked(event.fundsId)
    updateBackedFunds()
  }

  private def canUseFunds[C <: FiatCurrency](
      fundsId: ExchangeId, amount: CurrencyAmount[C]): Validation[String, BlockedFundsInfo[C]] = for {
    funds <- requireExistingFunds(fundsId, amount.currency)
    _ <- requireEnoughBalance(funds, amount)
    _ <- requiredBackedFunds(funds)
  } yield funds

  private def requireExistingFunds[C <: FiatCurrency](
      fundsId: ExchangeId, currency: C): Validation[String, BlockedFundsInfo[C]] =
    funds.get(fundsId).toSuccess(s"no such funds with id $fundsId")
      .ensure(s"cannot spend $currency out of $fundsId")(_.remainingAmount.currency == currency)
      .map(_.asInstanceOf[BlockedFundsInfo[C]])

  private def requireEnoughBalance[C <: FiatCurrency](
      funds: BlockedFundsInfo[C], minimumBalance: CurrencyAmount[C]): Validation[String, Unit] =
    if (funds.remainingAmount >= minimumBalance) ().success
    else s"""insufficient blocked funds for id ${funds.id}: $minimumBalance requested,
            |${funds.remainingAmount} available""".stripMargin.failure

  private def requiredBackedFunds(funds: BlockedFundsInfo[_]): Validation[String, Unit] =
    if (areBacked(funds)) ().success
    else s"funds with id ${funds.id} are not currently available".failure

  private def areBacked(blockedFunds: BlockedFundsInfo[_]): Boolean =
    backedFunds.contains(blockedFunds.id)

  private def updateFunds(newFunds: BlockedFundsInfo[_ <: FiatCurrency]): Unit = {
    funds += newFunds.id -> newFunds
  }

  private def updateBackedFunds(): Unit = {
    for (currency <- currenciesInUse()) {
      updateBackedFunds(currency)
    }
  }

  private def updateBackedFunds(currency: FiatCurrency): Unit = {
    val newlyBacked = fundsThatCanBeBacked(currency)
    val previouslyBacked = filterForCurrency(currency, backedFunds)
    val neverBacked = filterForCurrency(currency, neverBackedFunds)

    if (recoveryFinished) {
      notifyAvailabilityChanges(neverBacked, previouslyBacked, newlyBacked)
    }
    fundsForCurrency(currency).foreach(clearBacked)
    newlyBacked.foreach(setBacked)
  }

  private def fundsThatCanBeBacked[C <: FiatCurrency](currency: C): Set[ExchangeId] = {
    val availableBalance = balances.balanceFor(currency)
    val eligibleFunds = funds
      .values
      .filter(_.remainingAmount.currency == currency)
      .asInstanceOf[Iterable[BlockedFundsInfo[C]]]
      .toSeq
      .sortBy(f => arrivalOrder.indexOf(f.id))
    val fundsThatCanBeBacked =
      eligibleFunds.scanLeft(CurrencyAmount.zero(currency))(_ + _.remainingAmount)
        .takeWhile(_ <= availableBalance)
        .size - 1
    eligibleFunds.take(fundsThatCanBeBacked).map(_.id).toSet
  }

  private def notifyAvailabilityChanges(neverBacked: Set[ExchangeId],
                                        previouslyAvailable: Set[ExchangeId],
                                        currentlyAvailable: Set[ExchangeId]): Unit = {
    notifyListeners(
      PaymentProcessorActor.UnavailableFunds,
      (previouslyAvailable ++ neverBacked).diff(currentlyAvailable))
    notifyListeners(
      PaymentProcessorActor.AvailableFunds,
      currentlyAvailable.diff(previouslyAvailable))
  }

  private def notifyListeners(eventBuilder: ExchangeId => FundsAvailabilityEvent,
                              fundsIds: Iterable[ExchangeId]): Unit = {
    for (fundsId <- fundsIds) {
      context.system.eventStream.publish(eventBuilder(fundsId))
    }
  }

  private def currenciesInUse(): Set[FiatCurrency] =
    funds.values.map(_.remainingAmount.currency).toSet

  private def setBacked(funds: ExchangeId): Unit = {
    backedFunds += funds
  }

  private def setNeverBacked(funds: ExchangeId): Unit = {
    neverBackedFunds += funds
  }

  private def clearBacked(funds: ExchangeId): Unit = {
    backedFunds -= funds
    notBackedFunds -= funds
    neverBackedFunds -= funds
  }

  private def fundsForCurrency(currency: FiatCurrency): Set[ExchangeId] =
    funds.values.filter(_.remainingAmount.currency == currency).map(_.id).toSet

  private def filterForCurrency(currency: FiatCurrency, funds: Set[ExchangeId]): Set[ExchangeId] = {
    val currencyFunds = fundsForCurrency(currency)
    funds.filter(currencyFunds.contains)
  }

  private def totalBlockedForCurrency[C <: FiatCurrency](currency: C): Option[FiatAmount] = {
    val fundsForCurrency = funds.values
      .filter(_.remainingAmount.currency == currency)
      .asInstanceOf[Iterable[BlockedFundsInfo[C]]]
    if (fundsForCurrency.isEmpty) None
    else Some(fundsForCurrency.map(_.remainingAmount).reduce(_ + _))
  }
}

private[okpay] object BlockedFiatRegistry {

  val PersistenceId = "blockedFiatRegistry"

  case class RetrieveTotalBlockedFunds[C <: FiatCurrency](currency: C)
  case class TotalBlockedFunds[C <: FiatCurrency](funds: CurrencyAmount[C])

  case class BalancesUpdate(balances: Seq[FiatAmount])

  case class UseFunds(funds: ExchangeId, amount: FiatAmount)
  case class FundsUsed(funds: ExchangeId, amount: FiatAmount)
  case class CannotUseFunds(funds: ExchangeId, amount: FiatAmount, reason: String)

  private sealed trait StateEvents
  private case class FundsBlockedEvent(fundsId: ExchangeId, amount: FiatAmount) extends StateEvents
  private case class FundsUsedEvent(fundsId: ExchangeId, amount: FiatAmount) extends StateEvents
  private case class FundsUnblockedEvent(fundsId: ExchangeId) extends StateEvents

  def props = Props(new BlockedFiatRegistry(PersistenceId))
}
