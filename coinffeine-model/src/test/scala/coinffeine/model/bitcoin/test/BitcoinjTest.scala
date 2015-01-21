package coinffeine.model.bitcoin.test

import java.io.File
import java.util.concurrent.locks.{Lock, ReentrantLock}
import scala.annotation.tailrec
import scala.util.Try

import org.bitcoinj.core.{FullPrunedBlockChain, StoredBlock}
import org.bitcoinj.store.H2FullPrunedBlockStore
import org.bitcoinj.utils.BriefLogFormatter

import coinffeine.common.test.{TempDir, UnitTest}
import coinffeine.model.bitcoin._
import coinffeine.model.currency._

private object BitcoinjTest {
  /** Bitcoinj uses global state such as the TX fees than cannot be changed in isolation so we
    * need to serialize test executions. */
  val ExecutionLock: Lock = new ReentrantLock()
}

/** Base class for testing against an in-memory, validated blockchain.  */
trait BitcoinjTest extends UnitTest with CoinffeineUnitTestNetwork.Component {

  var blockStorePath: File = _
  var blockStore: H2FullPrunedBlockStore = _
  var chain: FullPrunedBlockChain = _

  before { startBitcoinj() }
  after { stopBitcoinj() }

  def chainHead(): StoredBlock = chain.getChainHead

  def withFees[A](body: => A) = {
    Wallet.defaultFeePerKb = MutableTransaction.ReferenceDefaultMinTxFee
    val result = Try(body)
    Wallet.defaultFeePerKb = Bitcoin.Zero
    result.get
  }

  def createWallet(): Wallet = {
    val wallet = new Wallet(network)
    chain.addWallet(wallet)
    wallet
  }

  def createWallet(key: KeyPair): Wallet = {
    val wallet = createWallet()
    wallet.importKey(key)
    wallet
  }

  /** Create a wallet and mine bitcoins into it until getting at least `amount` in its balance. */
  def createWallet(key: KeyPair, amount: Bitcoin.Amount): Wallet = {
    val wallet = createWallet(key)
    sendMoneyToWallet(wallet, amount)
    wallet
  }

  /** Create a wallet and mine bitcoins into it until getting at least `amount` in its balance. */
  def createWallet(amount: Bitcoin.Amount): Wallet = {
    val wallet = createWallet()
    sendMoneyToWallet(wallet, amount)
    wallet
  }

  /** Mine bitcoins into a wallet until having a minimum amount. */
  def sendMoneyToWallet(wallet: Wallet, amount: Bitcoin.Amount): Unit = {
    val miner = new KeyPair
    val minerWallet = createWallet(miner)
    while (
      minerWallet.getBalance < amount) {
      mineBlock(miner)
    }
    sendToBlockChain(minerWallet.createSend(wallet.freshReceiveAddress(), amount))
  }

  /** Mine a block and send the coinbase reward to the passed key. */
  def mineBlock(miner: PublicKey) = sendToBlockChain(miner)

  def mineBlock() = sendToBlockChain()

  def mineUntilLockTime(lockTime: Long): Unit = {
    while (blockStore.getChainHead.getHeight < lockTime) {
      mineBlock()
    }
  }

  /** Mine a new block with the passed transactions using the given last block.
    *
    * @param lastBlock The last block to be considered the chain head
    * @param miner     Destination key of the coinbase
    * @param txs       Transactions to include in the new block
    * @return          The new blockchain header
    */
  def sendToBlockChain(lastBlock: StoredBlock,
                       miner: PublicKey,
                       txs: MutableTransaction*): StoredBlock = {
    @tailrec
    def retrySend(remainingAttempts: Int): StoredBlock = {
      if (remainingAttempts < 0) {
        throw new IllegalStateException(
          "after several attempts, cannot send the given transactions to the blockchain")
      }
      val newBlock = lastBlock.getHeader.createNextBlockWithCoinbase(miner.getPubKey, 50.BTC)
      txs.foreach(newBlock.addTransaction)
      newBlock.solve()
      if (!chain.add(newBlock)) {
        Thread.sleep(250)
        retrySend(remainingAttempts - 1)
      }
      else chain.getBlockStore.get(newBlock.getHash)
    }
    retrySend(3)
  }

  /** Mine a new block with the passed transactions.
    *
    * @param miner  Destination key of the coinbase
    * @param txs    Transactions to include in the new block
    */
  def sendToBlockChain(miner: PublicKey, txs: MutableTransaction*): StoredBlock = {
    sendToBlockChain(blockStore.getChainHead, miner, txs: _*)
  }

  /** Mine a new block with the passed transactions. */
  def sendToBlockChain(txs: MutableTransaction*): StoredBlock =
    sendToBlockChain(new PublicKey(), txs: _*)

  /** Performs a serialization roundtrip to guarantee that it can be sent to a remote peer. */
  def throughWire(tx: MutableTransaction) = new MutableTransaction(network, tx.bitcoinSerialize())

  /** Performs a serialization roundtrip to guarantee that it can be sent to a remote peer. */
  def throughWire(sig: TransactionSignature) = TransactionSignature.decode(sig.encodeToBitcoin())

  def expectBlockHavingConfirmations(block: StoredBlock, confirmations: Int): Unit = {
    require(confirmations > 0)

    @tailrec
    def expectBlockAtDepth(depth: Int, chainHead: StoredBlock): Boolean = {
      if (depth == 1) chainHead.getHeader.getHash == block.getHeader.getHash
      else expectBlockAtDepth(depth - 1, chainHead.getPrev(blockStore))
    }

    withClue(s"block ${block.getHeader.getHash} have $confirmations confirmations") {
      blockStore.getChainHead.getHeight should be >= confirmations
      expectBlockAtDepth(confirmations, blockStore.getChainHead) shouldBe true
    }
  }

  private def startBitcoinj(): Unit = {
    BitcoinjTest.ExecutionLock.lock()
    BriefLogFormatter.init()
    Wallet.defaultFeePerKb = Bitcoin.Zero
    createH2BlockStore()
    chain = new FullPrunedBlockChain(network, blockStore)
  }

  private def createH2BlockStore(): Unit = {
    blockStorePath = TempDir.create("blockStore")
    blockStore = new H2FullPrunedBlockStore(network, new File(blockStorePath, "db").toString, 1000)
    blockStore.resetStore()
  }

  private def stopBitcoinj(): Unit = {
    try {
      blockStore.close()
      destroyH2BlockStore()
      Wallet.defaultFeePerKb = MutableTransaction.ReferenceDefaultMinTxFee
    } finally {
      BitcoinjTest.ExecutionLock.unlock()
    }
  }

  private def destroyH2BlockStore(): Unit = {
    blockStore.close()
    recursiveDelete(blockStorePath)
  }

  private def recursiveDelete(file: File): Unit = {
    val files = Option(file.listFiles()).getOrElse(Array.empty)
    files.foreach(recursiveDelete)
    file.delete()
  }
}
