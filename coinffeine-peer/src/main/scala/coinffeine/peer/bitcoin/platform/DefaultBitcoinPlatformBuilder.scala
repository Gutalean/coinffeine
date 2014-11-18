package coinffeine.peer.bitcoin.platform

import java.io.File
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

import org.bitcoinj.core.{AbstractBlockChain, PeerGroup, FullPrunedBlockChain}
import org.bitcoinj.store.MemoryFullPrunedBlockStore
import org.slf4j.LoggerFactory

import coinffeine.model.bitcoin._
import coinffeine.peer.bitcoin.wallet.SmartWallet

class DefaultBitcoinPlatformBuilder extends BitcoinPlatform.Builder {

  import DefaultBitcoinPlatformBuilder._

  private var network: Network with NetworkComponent.SeedPeers = _
  private var walletFile: Option[File] = None

  def setNetwork(value: Network with NetworkComponent.SeedPeers) = {
    network = value
    this
  }

  def setWalletFile(file: File) = {
    walletFile = Some(file)
    this
  }

  override def build(): BitcoinPlatform = {
    require(network != null, "Network should be defined")
    val blockchain = buildBlockchain()
    val peerGroup = buildPeerGroup(blockchain)
    val wallet = buildWallet(blockchain, peerGroup)
    new DefaultBitcoinPlatform(blockchain, peerGroup, wallet, network.seedPeers)
  }

  private def buildBlockchain() =
    new FullPrunedBlockChain(network, new MemoryFullPrunedBlockStore(network, FullStoredDepth))

  private def buildPeerGroup(blockchain: AbstractBlockChain) = {
    val pg = new PeerGroup(network, blockchain)
    pg.setMinBroadcastConnections(MinBroadcastConnections)
    pg
  }

  private def buildWallet(blockchain: AbstractBlockChain, peerGroup: PeerGroup) = {
    val wallet = walletFile.fold(buildInMemoryWallet())(buildFileBackedWallet)
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
    Log.info("Loading wallet from {}", walletFile)
    SmartWallet.loadFromFile(walletFile)
  }

  private def emptyWalletAt(walletFile: File): SmartWallet = {
    Log.warn("{} doesn't exists, starting with an empty wallet", walletFile)
    new SmartWallet(network)
  }
}

private object DefaultBitcoinPlatformBuilder {
  val Log = LoggerFactory.getLogger(classOf[DefaultBitcoinPlatformBuilder])
  val AutoSaveInterval = 250.millis
  val FullStoredDepth = 1000
  val MinBroadcastConnections = 1
}
