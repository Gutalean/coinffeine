package coinffeine.peer.bitcoin.platform

import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

import com.typesafe.scalalogging.StrictLogging
import org.bitcoinj.core._
import org.bitcoinj.store._

import coinffeine.model.bitcoin._
import coinffeine.peer.bitcoin.wallet.SmartWallet

class DefaultBitcoinPlatformBuilder extends StrictLogging {

  import DefaultBitcoinPlatformBuilder._

  private val feeCalculator = TransactionSizeFeeCalculator

  private var network: Network with NetworkComponent.SeedPeers = _
  private var walletFile: Option[File] = None
  private var blockchainFile: Option[File] = None
  private var checkpoints: Option[URL] = None
  private var spvEnabled: Boolean = true

  def setNetwork(value: Network with NetworkComponent.SeedPeers) = {
    network = value
    this
  }

  def setWalletFile(file: File) = {
    walletFile = Some(file)
    this
  }

  def setBlockchainFile(file: File) = {
    blockchainFile = Some(file)
    this
  }

  def enableSpv(enabled: Boolean) = {
    spvEnabled = enabled
    this
  }

  def setCheckpoints(url: Option[URL]) = {
    checkpoints = url
    this
  }

  def build(): BitcoinPlatform = {
    require(network != null, "Network should be defined")
    val preexistingChainFile = blockchainFile.exists(_.exists())
    val shouldReplayWallet = exists(walletFile) && !exists(blockchainFile)
    val blockchain = buildBlockchain()
    val peerGroup = buildPeerGroup(blockchain)
    val wallet = buildWallet(blockchain, peerGroup, shouldReplayWallet)
    setupCheckpoints(blockchain, wallet.delegate.getEarliestKeyCreationTime, preexistingChainFile)
    new DefaultBitcoinPlatform(network, blockchain, peerGroup, wallet, network.seedPeers)
  }

  private def buildBlockchain(): AbstractBlockChain = {
    if (spvEnabled) buildSpvBlockchain() else buildH2FullBlockchain()
  }

  private def buildSpvBlockchain(): BlockChain = {
    def inMemoryStore = new MemoryFullPrunedBlockStore(network, FullStoredDepth)
    def fileBackedStore(file: File) = new SPVBlockStore(network, file)
    new BlockChain(network, blockchainFile.fold[BlockStore](inMemoryStore)(fileBackedStore))
  }

  private def buildH2FullBlockchain(): FullPrunedBlockChain = {
    val file = blockchainFile.getOrElse(throw new scala.IllegalArgumentException(
      s"Cannot initialize an H2 store on path = $blockchainFile"))
    val store = new H2FullPrunedBlockStore(network, toH2Scheme(file), Int.MaxValue)
    val blockchain = new FullPrunedBlockChain(network, store)
    blockchain.setRunScripts(false)
    blockchain
  }

  private def toH2Scheme(file: File): String = file.toString match {
    case H2FileName(prefix) => s"file:$prefix"
    case other => throw new scala.IllegalArgumentException(s"Invalid H2 filename: $other")
  }

  private def buildPeerGroup(blockchain: AbstractBlockChain) = {
    val pg = new PeerGroup(network, blockchain)
    pg.setMinBroadcastConnections(MinBroadcastConnections)
    pg
  }

  private def buildWallet(blockchain: AbstractBlockChain,
                          peerGroup: PeerGroup,
                          shouldReplayWallet: Boolean) = {
    val wallet = walletFile.fold(buildInMemoryWallet())(buildFileBackedWallet)
    if (shouldReplayWallet) {
      wallet.delegate.clearTransactions(0)
    }
    blockchain.addWallet(wallet.delegate)
    peerGroup.addWallet(wallet.delegate)
    wallet
  }

  private def buildInMemoryWallet() = new SmartWallet(network, feeCalculator)

  private def buildFileBackedWallet(walletFile: File) = {
    val wallet = if (walletFile.exists()) loadFromFile(walletFile) else emptyWalletAt(walletFile)
    wallet.delegate.autosaveToFile(walletFile, AutoSaveInterval.toMillis, TimeUnit.MILLISECONDS, null)
    wallet
  }

  private def loadFromFile(walletFile: File): SmartWallet = {
    logger.info("Loading wallet from {}", walletFile)
    SmartWallet.loadFromFile(walletFile, feeCalculator)
  }

  private def emptyWalletAt(walletFile: File): SmartWallet = {
    logger.warn("{} doesn't exists, starting with an empty wallet", walletFile)
    new SmartWallet(network, feeCalculator)
  }

  private def exists(maybeFile: Option[File]): Boolean = maybeFile.fold(false)(_.exists())

  private def setupCheckpoints(
      blockchain: AbstractBlockChain,
      earliestKeyTime: Long,
      preexistingChainFile: Boolean): Unit = {
    if (!spvEnabled) logger.debug("Skipping checkpointing: using a full blockchain")
    else if (preexistingChainFile) logger.debug("Skipping checkpointing: existing blockfile")
    else if (checkpoints.isEmpty) logger.debug("Skipping checkpointing: no checkpoints file")
    else checkpoint(blockchain, earliestKeyTime)
  }

  private def checkpoint(blockchain: AbstractBlockChain, earliestKeyTime: Long): Unit = {
    logger.info(s"Checkpointing with ${checkpoints.get}")
    val stream = checkpoints.get.openStream()
    try {
      CheckpointManager.checkpoint(
        network, stream, blockchain.getBlockStore, earliestKeyTime)
    } finally {
      stream.close()
    }
  }
}

private object DefaultBitcoinPlatformBuilder {
  val AutoSaveInterval = 250.millis
  val FullStoredDepth = 1000
  val MinBroadcastConnections = 1
  private val H2FileName = """(.*)\.h2\.db""".r
}
