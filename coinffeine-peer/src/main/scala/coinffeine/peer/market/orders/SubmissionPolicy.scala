package coinffeine.peer.market.orders

import coinffeine.model.bitcoin.TransactionSizeFeeCalculator
import coinffeine.model.currency.balance.{BitcoinBalance, FiatBalance}
import coinffeine.model.currency.{Bitcoin, BitcoinAmount, FiatAmount, FiatCurrency}
import coinffeine.model.exchange.Role
import coinffeine.model.market.{OrderBookEntry, Spread}
import coinffeine.model.order.{Ask, Bid, OrderRequest}
import coinffeine.model.util.{CacheStatus, Cached}
import coinffeine.peer.amounts.{AmountsCalculator, DefaultAmountsCalculator, HappyPathTransactions}

trait SubmissionPolicy {
  def entry: Option[OrderBookEntry]
  def setEntry(entry: OrderBookEntry): Unit
  def unsetEntry(): Unit
  def setBitcoinBalance(balance: BitcoinBalance): Unit
  def setFiatBalance(balance: Cached[FiatBalance]): Unit
  def entryToSubmit: Option[OrderBookEntry]
}

class SubmissionPolicyImpl(
    calculator: AmountsCalculator = new DefaultAmountsCalculator()) extends SubmissionPolicy {

  private var _entry: Option[OrderBookEntry] = None
  private var bitcoinBalance = BitcoinBalance.empty
  private var fiatBalance = Cached.stale(FiatBalance.empty)

  override def entry = _entry

  override def setEntry(entry: OrderBookEntry): Unit = {
    this._entry = Some(entry)
  }

  override def unsetEntry(): Unit = {
    this._entry = None
  }

  override def setBitcoinBalance(balance: BitcoinBalance): Unit = {
    bitcoinBalance = balance
  }

  override def setFiatBalance(balance: Cached[FiatBalance]): Unit = {
    fiatBalance = balance
  }

  override def entryToSubmit: Option[OrderBookEntry] = _entry.filter(canAfford)

  private def canAfford(entry: OrderBookEntry): Boolean =
    if (entry.price.isLimited) canAffordLimitOrder(entry)
    else canAffordMarketPriceOrder(entry)

  private def canAffordLimitOrder(entry: OrderBookEntry): Boolean = {
    calculator.estimateAmountsFor(toOrderRequest(entry), Spread.empty) match {
      case Some(amounts) =>
        val role = Role.fromOrderType(entry.orderType)
        availableAmounts(
          role.select(amounts.bitcoinRequired),
          role.select(amounts.fiatRequired)
        )
      case None => false
    }
  }

  private def canAffordMarketPriceOrder(entry: OrderBookEntry): Boolean = {
    availableAmounts(
      bitcoinRequired = entry.orderType match {
        case Bid => SubmissionPolicyImpl.MinimumBuyerDeposit
        case Ask => entry.amount
      },
      fiatRequired = entry.orderType match {
        case Bid => entry.price.currency.fromUnits(1)
        case Ask => entry.price.currency.zero
      }
    )
  }

  private def availableAmounts(bitcoinRequired: BitcoinAmount, fiatRequired: FiatAmount): Boolean = {
    availableBitcoin >= bitcoinRequired && availableFiat(fiatRequired.currency) >= fiatRequired
  }

  private def availableBitcoin: BitcoinAmount =
    bitcoinBalance.available - bitcoinBalance.blocked

  private def availableFiat(currency: FiatCurrency): FiatAmount = fiatBalance match {
    case Cached(_, CacheStatus.Stale) => currency.zero
    case Cached(balance, CacheStatus.Fresh) => availableFiat(currency, balance)
  }

  private def availableFiat(currency: FiatCurrency, balance: FiatBalance): FiatAmount = {
    val notBlocked =
      balance.amounts.getOrZero(currency) - balance.blockedAmounts.getOrZero(currency)
    val maybeLimit = balance.remainingLimits.get(currency)
    maybeLimit.fold(notBlocked)(_ min notBlocked)
  }

  private def toOrderRequest(entry: OrderBookEntry) =
    OrderRequest(entry.orderType, entry.amount, entry.price)
}

object SubmissionPolicyImpl {
  val MinimumBuyerDeposit =
    TransactionSizeFeeCalculator.defaultTransactionFee * HappyPathTransactions + Bitcoin.satoshi
}
