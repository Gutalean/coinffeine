package coinffeine.peer.bitcoin

import java.io.File
import java.util.concurrent.TimeUnit

import org.bitcoinj.core._
import org.bitcoinj.store.MemoryFullPrunedBlockStore
import org.slf4j.LoggerFactory

import coinffeine.model.bitcoin._
import coinffeine.model.bitcoin.network.{IntegrationTestNetwork, PublicTestNetwork, MainNetwork}
import coinffeine.peer.bitcoin.wallet.SmartWallet
import coinffeine.peer.config.ConfigComponent

trait DefaultBitcoinComponents
    extends PeerGroupComponent with BlockchainComponent with WalletComponent with NetworkComponent {
  this: ConfigComponent =>

  private lazy val configuredNetwork: NetworkComponent =
    configProvider.bitcoinSettings().network match {
      case BitcoinSettings.IntegrationTestnet => new IntegrationTestNetwork.Component {}
      case BitcoinSettings.PublicTestnet => new PublicTestNetwork.Component {}
      case BitcoinSettings.MainNet => MainNetwork
    }
  override lazy val network = configuredNetwork.network
  override lazy val peerAddresses = configuredNetwork.peerAddresses

  override lazy val blockchain: AbstractBlockChain = {
    val blockStore = new MemoryFullPrunedBlockStore(network, 1000)
    new FullPrunedBlockChain(network, blockStore)
  }

  override lazy val peerGroup = {
    val peerGroup = new PeerGroup(network, blockchain)
    peerAddresses.foreach(peerGroup.addAddress)
    peerGroup
  }

  override lazy val wallet = {
    val walletFile = configProvider.bitcoinSettings().walletFile
    val wallet =
      if (walletFile.exists()) loadFromFile(walletFile)
      else emptyWalletAt(walletFile)
    setupWallet(wallet, walletFile)
    wallet
  }

  private def loadFromFile(walletFile: File): SmartWallet = {
    DefaultBitcoinComponents.Log.info("Loading wallet from {}", walletFile)
    SmartWallet.loadFromFile(walletFile)
  }

  private def emptyWalletAt(walletFile: File): SmartWallet = {
    DefaultBitcoinComponents.Log.warn("{} doesn't exists, starting with an empty wallet", walletFile)
    new SmartWallet(network)
  }

  private def setupWallet(wallet: SmartWallet, walletFile: File): Unit = {
    blockchain.addWallet(wallet.delegate)
    peerGroup.addWallet(wallet.delegate)
    wallet.delegate.autosaveToFile(walletFile, 250, TimeUnit.MILLISECONDS, null)
  }
}

object DefaultBitcoinComponents {
  private val Log = LoggerFactory.getLogger(classOf[DefaultBitcoinComponents])
}
