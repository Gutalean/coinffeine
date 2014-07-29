package coinffeine.peer.payment.okpay

import akka.actor.ActorRef

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency, FiatAmount}
import coinffeine.peer.payment.PaymentProcessorActor.FundsId

private[okpay] class BlockedFunds {
  private case class Funds(remainingAmount: FiatAmount, listener: ActorRef)
  private var createdFunds = 0
  private var fundsMap = Map.empty[FundsId, Funds]
  private var _balances = Seq.empty[FiatAmount]

  def listenersByFund: Map[FundsId, ActorRef] = fundsMap.mapValues(_.listener)

  def updateBalances(balances: Seq[FiatAmount]): Unit = {
    _balances = balances
  }

  def block(amount: FiatAmount, listener: ActorRef): Option[FundsId] = {
    if (canBlock(amount)) {
      val id = nextFundsId()
      fundsMap += id -> Funds(amount, listener)
      Some(id)
    } else None
  }

  def unblock(id: FundsId): Unit = {
    fundsMap -= id
  }

  def areFundsBacked: Boolean = {
    val currenciesBlocked = fundsMap.values.map(_.remainingAmount.currency).toSet
    currenciesBlocked.forall { currency =>
      blockedIn(currency) <= balanceIn(currency)
    }
  }

  private def canBlock(amount: FiatAmount): Boolean = {
    val currency = amount.currency
    val unassignedBalance = balanceIn(currency) - blockedIn(currency)
    amount <= unassignedBalance
  }

  private def balanceIn[C <: FiatCurrency](currency: C): CurrencyAmount[C] =
    _balances.find(_.currency == currency)
      .getOrElse(currency.Zero)
      .asInstanceOf[CurrencyAmount[C]]

  private def blockedIn[C <: FiatCurrency](currency: C): CurrencyAmount[C] =
    currency.amount(fundsMap.values.collect {
      case Funds(CurrencyAmount(value, `currency`), _) => value
    }.sum)

  private def nextFundsId(): FundsId = {
    createdFunds += 1
    FundsId(createdFunds)
  }
}
