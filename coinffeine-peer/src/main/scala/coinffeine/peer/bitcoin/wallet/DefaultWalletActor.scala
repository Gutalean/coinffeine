package coinffeine.peer.bitcoin.wallet

import scala.collection.JavaConversions._
import scala.util.control.NonFatal

import akka.actor.{Address => _, _}
import akka.persistence.{PersistentActor, SnapshotOffer}
import org.bitcoinj.core.TransactionOutPoint

import coinffeine.common.akka.event.CoinffeineEventProducer
import coinffeine.common.akka.persistence.{PeriodicSnapshot, PersistentEvent}
import coinffeine.model.bitcoin._
import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.currency.balance.BitcoinBalance
import coinffeine.model.exchange.ExchangeId
import coinffeine.peer.bitcoin.wallet.BlockedOutputs.Output
import coinffeine.peer.bitcoin.wallet.WalletActor._
import coinffeine.peer.events.bitcoin.{BitcoinBalanceChanged, PrimaryAddressChanged, WalletActivityChanged}

private class DefaultWalletActor(
    wallet: SmartWallet,
    override val persistenceId: String)
    extends PersistentActor with PeriodicSnapshot with ActorLogging with CoinffeineEventProducer {

  import DefaultWalletActor._

  private var blockedOutputs = new BlockedOutputs()
  private var deposits = Map.empty[Hash, ExchangeId]
  private var listeners = Set.empty[ActorRef]

  override def preStart(): Unit = {
    subscribeToWalletChanges()
    updateBalance()
    updateWalletPrimaryKeys()
    updateActivity()
    super.preStart()
  }

  override def postStop(): Unit = {
    super.postStop()
    unsubscribeFromWalletChanges()
  }

  override def receiveRecover: Receive = {
    case event: FundsBlocked => onFundsBlocked(event)
    case event: FundsUnblocked => onFundsUnblocked(event)
    case event: DepositCreated => onDepositCreated(event)
    case event: DepositCancelled => onDepositCancelled(event)
    case SnapshotOffer(metadata, snapshot: Snapshot) =>
      setLastSnapshot(metadata.sequenceNr)
      blockedOutputs = snapshot.blockedOutputs
      deposits = snapshot.deposits
  }

  override protected def createSnapshot: Option[PersistentEvent] =
    Some(Snapshot(blockedOutputs, deposits))

  override def receiveCommand: Receive = managingSnapshots orElse {

    case request@CreateDeposit(coinsId, signatures, amount, transactionFee) =>
      blockedOutputs.canUse(coinsId, amount + transactionFee) match {
        case scalaz.Success(outputs) =>
          log.info("Creating deposit of {} (+{} fee) for {} with signatures {}",
            amount, transactionFee, coinsId, signatures)
          persist(DepositCreated(request, outputs)) { event =>
            val tx = onDepositCreated(event)
            sender ! WalletActor.DepositCreated(request, tx)
          }
        case scalaz.Failure(error) =>
          log.error("Failed to create deposit of {} (+{} fee) for {} with signatures {}",
            amount, transactionFee, coinsId, signatures)
          sender() ! WalletActor.DepositCreationError(request, error)
      }

    case req@WalletActor.CreateTransaction(amount, to) =>
      try {
        sender ! WalletActor.TransactionCreated(req, createTransaction(amount, to))
      } catch {
        case NonFatal(ex) => sender ! WalletActor.TransactionCreationFailure(req, ex)
      }

    case WalletActor.ReleaseDeposit(tx) =>
      log.info("Releasing deposit on transaction {}", tx.get.getHashAsString)
      persist(DepositCancelled(tx))(onDepositCancelled)

    case CreateKeyPair =>
      sender() ! KeyPairCreated(wallet.freshKeyPair())

    case InternalWalletChanged =>
      updateBalance()
      updateActivity()
      notifyListeners()

    case BlockBitcoins(fundsId, _) if blockedOutputs.areBlocked(fundsId) =>
      sender() ! BlockedBitcoins(fundsId)

    case BlockBitcoins(fundsId, amount) =>
      blockedOutputs.collectFunds(amount) match {
        case Some(funds) =>
          log.info("Blocking {} for {}", funds, fundsId)
          persist(FundsBlocked(fundsId, funds)) { event =>
            onFundsBlocked(event)
            sender() ! BlockedBitcoins(fundsId)
          }
        case None =>
          log.error("Failed to block {} for {}", amount, sender())
          sender() ! CannotBlockBitcoins(
            s"cannot collect funds to block $amount: ${blockedOutputs.available} available")
      }

    case UnblockBitcoins(fundsId) if blockedOutputs.areBlocked(fundsId) =>
      log.info("Unblocking funds for {}", fundsId)
      persist(FundsUnblocked(fundsId))(onFundsUnblocked)

    case SubscribeToWalletChanges =>
      context.watch(sender())
      listeners += sender()

    case UnsubscribeToWalletChanges =>
      context.unwatch(sender())
      listeners -= sender()

    case Terminated(listener) =>
      listeners -= listener
  }

  private def onFundsBlocked(event: FundsBlocked): Unit = {
    blockedOutputs.block(event.id, event.outputs)
  }

  private def onFundsUnblocked(event: FundsUnblocked): Unit = {
    blockedOutputs.unblock(event.id)
  }

  private def onDepositCreated(event: DepositCreated): ImmutableTransaction = {
    import event.request._
    blockedOutputs.use(coinsId, event.outputs)
    val outPoints = toOutPoints(event.outputs)
    val tx = wallet.findTransactionSpendingOutput(outPoints.head).getOrElse(
      wallet.createMultisignTransaction(outPoints, requiredSignatures, amount, transactionFee))
    deposits += tx.get.getHash -> coinsId
    if (recoveryFinished) {
      log.debug(s"Creating deposit for $coinsId using outputs ${event.outputs} and having TX $tx")
    }
    tx
  }

  private def onDepositCancelled(event: DepositCancelled): Unit = {
    val releasedOutputs = event.tx.get.getInputs.map(_.getOutpoint).toSet
    blockedOutputs.cancelUsage(toOutputs(releasedOutputs))
  }

  private def updateActivity(): Unit = {
    val transactions = wallet.delegate.getTransactionsByTime
    val activity = WalletActivity(deposits, wallet.delegate, transactions: _*)
    publish(WalletActivityChanged(activity))
  }

  private def updateBalance(): Unit = {
    blockedOutputs.setSpendCandidates(computeSpendCandidates)
    val balance = BitcoinBalance(
      estimated = wallet.estimatedBalance,
      available = wallet.availableBalance,
      minOutput = blockedOutputs.minOutput,
      blocked = blockedOutputs.blocked
    )
    publish(BitcoinBalanceChanged(balance))
  }

  private def createTransaction(amount: BitcoinAmount, to: Address): ImmutableTransaction = {
    val funds = blockedOutputs.collectUnblockedFunds(amount)
        .getOrElse(throw new Exception("Not enough funds"))
    wallet.createTransaction(toOutPoints(funds), amount, to)
  }

  private def computeSpendCandidates: Set[Output] =
    toOutputs(wallet.spendCandidates.map(_.getOutPointFor).toSet)

  private def toOutPoints(outputs: Set[Output]): Set[TransactionOutPoint] = outputs.map {
    output =>
      wallet.delegate.getTransaction(output.txHash)
          .getOutput(output.index)
          .getOutPointFor
  }

  private def toOutputs(outPoints: Set[TransactionOutPoint]): Set[Output] =
    outPoints.map { outPoint =>
      val output = Option(outPoint.getConnectedOutput).getOrElse(toOutput(outPoint))
      Output(outPoint.getHash, outPoint.getIndex.toInt, output.getValue)
    }

  private def toOutput(outPoint: TransactionOutPoint): MutableTransactionOutput = {
    wallet.delegate.getTransaction(outPoint.getHash).getOutput(outPoint.getIndex.toInt)
  }

  private def updateWalletPrimaryKeys(): Unit = {
    publish(PrimaryAddressChanged(wallet.currentReceiveAddress))
  }

  private def subscribeToWalletChanges(): Unit = {
    wallet.addListener(WalletListener)(context.dispatcher)
  }

  private def unsubscribeFromWalletChanges(): Unit = {
    wallet.removeListener(WalletListener)
  }

  private def notifyListeners(): Unit = {
    listeners.foreach(_ ! WalletChanged)
  }

  private object WalletListener extends SmartWallet.Listener {
    override def onChange() = {
      self ! InternalWalletChanged
    }
  }

}

object DefaultWalletActor {
  val PersistenceId = "wallet"

  def props(
      wallet: SmartWallet,
      persistenceId: String = PersistenceId) =
    Props(new DefaultWalletActor(wallet, persistenceId))

  private case object InternalWalletChanged

  private case class FundsBlocked(id: ExchangeId, outputs: Set[Output]) extends PersistentEvent

  private case class FundsUnblocked(id: ExchangeId) extends PersistentEvent

  private case class DepositCreated(request: CreateDeposit, outputs: Set[Output])
      extends PersistentEvent

  private case class DepositCancelled(tx: ImmutableTransaction) extends PersistentEvent

  private case class Snapshot(
      blockedOutputs: BlockedOutputs, deposits: Map[Hash, ExchangeId]) extends PersistentEvent

}
