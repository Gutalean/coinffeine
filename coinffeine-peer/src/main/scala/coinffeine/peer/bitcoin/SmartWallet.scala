package coinffeine.peer.bitcoin

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext

import com.google.bitcoin.core.{TransactionConfidence, Transaction, AbstractWalletEventListener}

import coinffeine.model.bitcoin._
import coinffeine.model.bitcoin.Implicits._
import coinffeine.model.currency._

class SmartWallet(wallet: Wallet) {

  import SmartWallet._

  private val blockedOutputs = {
    val outputs = new BlockedOutputs
    outputs.setSpendCandidates(wallet.calculateAllSpendCandidates(true).toSet)
    outputs
  }

  private var listeners: Set[(Listener, ExecutionContext)] = Set.empty

  def addListener(listener: Listener)(implicit executor: ExecutionContext) = synchronized {
    listeners += listener -> executor
  }

  def addresses: Seq[Address] = synchronized {
    val network = wallet.getNetworkParameters
    wallet.getKeys.map(_.toAddress(network))
  }

  def balance: BitcoinAmount = synchronized { wallet.balance() }

  def createKeyPair(): KeyPair = synchronized {
    val keyPair = new KeyPair()
    wallet.addKey(keyPair)
    keyPair
  }

  def blockFunds(amount: BitcoinAmount): Option[BlockedCoinsId] = synchronized {
    blockedOutputs.block(amount)
  }

  def unblockFunds(coinsId: BlockedCoinsId): Unit = synchronized {
    blockedOutputs.unblock(coinsId)
  }

  def createTransaction(amount: BitcoinAmount, to: Address): ImmutableTransaction = synchronized {
    require(amount < blockedOutputs.spendable,
      s"cannot create a transaction of $amount: not enough funds in wallet")
    ImmutableTransaction(wallet.blockFunds(to, amount))
  }

  def createMultisignTransaction(coinsId: BlockedCoinsId,
                                 amount: BitcoinAmount,
                                 fee: BitcoinAmount,
                                 signatures: Seq[KeyPair]): ImmutableTransaction = synchronized {
    val inputs = blockedOutputs.use(coinsId, amount + fee)
    ImmutableTransaction(wallet.blockMultisignFunds(inputs, signatures, amount, fee))
  }

  def releaseTransaction(tx: ImmutableTransaction): Unit = synchronized {
    wallet.releaseFunds(tx.get)
    val releasedOutputs = for {
      input <- tx.get.getInputs.toList
      parentTx <- Option(wallet.getTransaction(input.getOutpoint.getHash))
      output <- Option(parentTx.getOutput(input.getOutpoint.getIndex.toInt))
    } yield output
    blockedOutputs.cancelUsage(releasedOutputs.toSet)
  }

  private def update(): Unit = synchronized {
    blockedOutputs.setSpendCandidates(wallet.calculateAllSpendCandidates(true).toSet)
    listeners.foreach { case (l, e) => e.execute(new Runnable {
      override def run() = l.onChange()
    })}
  }

  wallet.addEventListener(new AbstractWalletEventListener {

    override def onTransactionConfidenceChanged(wallet: Wallet, tx: Transaction): Unit = {
      // Don't notify confidence changes for already confirmed transactions to reduce load
      if (tx.getConfidence.getConfidenceType != TransactionConfidence.ConfidenceType.BUILDING ||
        tx.getConfidence.getDepthInBlocks == 1) {
        onChange()
      }
    }

    override def onChange(): Unit = {
      update()
    }
  })
}

object SmartWallet {

  trait Listener {
    def onChange(): Unit
  }
}
