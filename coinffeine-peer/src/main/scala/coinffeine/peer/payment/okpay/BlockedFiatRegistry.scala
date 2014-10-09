package coinffeine.peer.payment.okpay

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

import coinffeine.model.currency.{CurrencyAmount, FiatAmount, FiatCurrency}
import coinffeine.model.exchange.ExchangeId
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.peer.payment.PaymentProcessorActor.FundsAvailabilityEvent

private[okpay] class BlockedFiatRegistry extends Actor with ActorLogging {
  import coinffeine.peer.payment.okpay.BlockedFiatRegistry._

  private case class BlockedFundsInfo[C <: FiatCurrency](
      id: ExchangeId, remainingAmount: CurrencyAmount[C], @deprecated listener: ActorRef) {

    def canUseFunds(amount: CurrencyAmount[C]): Boolean = amount <= remainingAmount
  }

  private var balances: Map[FiatCurrency, FiatAmount] = Map.empty
  private var funds: Map[ExchangeId, BlockedFundsInfo[_ <: FiatCurrency]] = Map.empty
  private var arrivalOrder: Seq[ExchangeId] = Seq.empty
  private var backedFunds = Set.empty[ExchangeId]
  private var notBackedFunds = Set.empty[ExchangeId]
  private var neverBackedFunds = Set.empty[ExchangeId]

  override def receive: Receive = {
    case RetrieveTotalBlockedFunds(currency) =>
      totalBlockedForCurrency(currency) match {
        case Some(blockedFunds) => sender ! BlockedFiatRegistry.TotalBlockedFunds(blockedFunds)
        case None => sender ! BlockedFiatRegistry.TotalBlockedFunds(currency.Zero)
      }

    case BalancesUpdate(newBalances) =>
      balances = newBalances.map(b => b.currency -> b).toMap
      updateBackedFunds()

    case UseFunds(fundsId, amount) =>
      useFunds(fundsId, amount, sender())
      updateBackedFunds()

    case PaymentProcessorActor.BlockFunds(fundsId, _) if funds.contains(fundsId) =>
      sender() ! PaymentProcessorActor.AlreadyBlockedFunds(fundsId)

    case PaymentProcessorActor.BlockFunds(fundsId, amount) =>
      sender() ! PaymentProcessorActor.BlockedFunds(fundsId)
      arrivalOrder :+= fundsId
      funds += fundsId -> BlockedFundsInfo(fundsId, amount, sender())
      setNeverBacked(fundsId)
      updateBackedFunds()

    case PaymentProcessorActor.UnblockFunds(fundsId) =>
      arrivalOrder = arrivalOrder.filter(_ != fundsId)
      funds -= fundsId
      clearBacked(fundsId)
      updateBackedFunds()
  }

  private def useFunds[C <: FiatCurrency](fundsId: ExchangeId,
                                          amount: CurrencyAmount[C],
                                          requester: ActorRef): Unit = {
    funds.get(fundsId) match {
      case Some(blockedFunds) if blockedFunds.remainingAmount.currency != amount.currency =>
        throw new IllegalArgumentException(s"Cannot use $amount out of ${blockedFunds.remainingAmount}")
      case Some(blockedFunds: BlockedFundsInfo[C]) =>
        if (amount > blockedFunds.remainingAmount) {
          requester ! CannotUseFunds(fundsId, amount,
            s"insufficient blocked funds for id $fundsId: " +
              s"$amount requested, ${blockedFunds.remainingAmount} available")
        } else if (!areBacked(blockedFunds)) {
          requester ! CannotUseFunds(
            fundsId, amount, s"funds with id $fundsId are not currently blocked")
        } else {
          updateFunds(blockedFunds.copy(remainingAmount = blockedFunds.remainingAmount - amount))
          reduceBalance(amount)
          requester ! FundsUsed(fundsId, amount)
        }
      case None =>
        requester ! CannotUseFunds(fundsId, amount, s"no such funds with id $fundsId")
    }
  }

  private def areBacked(blockedFunds: BlockedFundsInfo[_]): Boolean =
    backedFunds.contains(blockedFunds.id)

  private def updateFunds(newFunds: BlockedFundsInfo[_ <: FiatCurrency]): Unit = {
    funds += newFunds.id -> newFunds
  }

  private def reduceBalance[C <: FiatCurrency](amount: CurrencyAmount[C]): Unit = {
    val prevAmount = balances.getOrElse(amount.currency, CurrencyAmount.zero(amount.currency))
      .asInstanceOf[CurrencyAmount[C]]
    require(amount <= prevAmount)
    balances += amount.currency -> (prevAmount - amount)
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

    notifyAvailabilityChanges(neverBacked, previouslyBacked, newlyBacked)

    fundsForCurrency(currency).foreach(clearBacked)
    newlyBacked.foreach(setBacked)
  }

  private def fundsThatCanBeBacked[C <: FiatCurrency](currency: C): Set[ExchangeId] = {
    val availableBalance = balances.getOrElse(currency, currency.Zero)
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
    for {
      fundsId <- fundsIds
      BlockedFundsInfo(_, _, listener) <- funds.get(fundsId)
    } {
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

  case class RetrieveTotalBlockedFunds[C <: FiatCurrency](currency: C)
  case class TotalBlockedFunds[C <: FiatCurrency](funds: CurrencyAmount[C])

  case class BalancesUpdate(balances: Seq[FiatAmount])

  case class UseFunds(funds: ExchangeId, amount: FiatAmount)
  case class FundsUsed(funds: ExchangeId, amount: FiatAmount)
  case class CannotUseFunds(funds: ExchangeId, amount: FiatAmount, reason: String)

  def props = Props(new BlockedFiatRegistry)
}
