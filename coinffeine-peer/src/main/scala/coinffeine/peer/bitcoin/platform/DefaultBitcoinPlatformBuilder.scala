package coinffeine.peer.bitcoin.platform

import java.io.File
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

import com.typesafe.scalalogging.StrictLogging
import org.bitcoinj.core._
import org.bitcoinj.store.{SPVBlockStore, MemoryFullPrunedBlockStore}

import coinffeine.model.bitcoin._
import coinffeine.peer.bitcoin.wallet.SmartWallet

class DefaultBitcoinPlatformBuilder extends BitcoinPlatform.Builder with StrictLogging {

  import DefaultBitcoinPlatformBuilder._

  private var network: Network with NetworkComponent.SeedPeers = _
  private var walletFile: Option[File] = None
  private var blockchainFile: Option[File] = None

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

  override def build(): BitcoinPlatform = {
    require(network != null, "Network should be defined")
    val shouldReplayWallet = exists(walletFile) && !exists(blockchainFile)
    val blockchain = buildBlockchain()
    val peerGroup = buildPeerGroup(blockchain)
    val wallet = buildWallet(blockchain, peerGroup, shouldReplayWallet)
    new DefaultBitcoinPlatform(blockchain, peerGroup, wallet, network.seedPeers)
  }

  private def buildBlockchain() =
    blockchainFile.fold[AbstractBlockChain](buildInMemoryBlockchain())(buildFileBackedBlockchain)

  private def buildInMemoryBlockchain() =
    new FullPrunedBlockChain(network, new MemoryFullPrunedBlockStore(network, FullStoredDepth))

  private def buildFileBackedBlockchain(file: File) =
    new BlockChain(network, new SPVBlockStore(network, file))

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

  private def buildInMemoryWallet() = new SmartWallet(network)

  private def buildFileBackedWallet(walletFile: File) = {
    val wallet = if (walletFile.exists()) loadFromFile(walletFile) else emptyWalletAt(walletFile)
    wallet.delegate.autosaveToFile(walletFile, AutoSaveInterval.toMillis, TimeUnit.MILLISECONDS, null)
    wallet
  }

  private def loadFromFile(walletFile: File): SmartWallet = {
    logger.info("Loading wallet from {}", walletFile)
    SmartWallet.loadFromFile(walletFile)
  }

  private def emptyWalletAt(walletFile: File): SmartWallet = {
    logger.warn("{} doesn't exists, starting with an empty wallet", walletFile)
    new SmartWallet(network)
  }

  private def exists(maybeFile: Option[File]): Boolean = maybeFile.fold(false)(_.exists())
}

private object DefaultBitcoinPlatformBuilder {
  val AutoSaveInterval = 250.millis
  val FullStoredDepth = 1000
  val MinBroadcastConnections = 1
}
