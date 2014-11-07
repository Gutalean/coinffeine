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

trait DefaultBitcoinComponents extends BlockchainComponent with WalletComponent with NetworkComponent {
  this: ConfigComponent =>

  private lazy val configuredNetwork: NetworkComponent =
    configProvider.bitcoinSettings().network match {
      case BitcoinSettings.IntegrationTestnet => new IntegrationTestNetwork.Component {}
      case BitcoinSettings.PublicTestnet => new PublicTestNetwork.Component {}
      case BitcoinSettings.MainNet => MainNetwork
    }
  override lazy val network = configuredNetwork.network
  override def seedPeerAddresses() = configuredNetwork.seedPeerAddresses()

  override lazy val blockchain: AbstractBlockChain = {
    val blockStore = new MemoryFullPrunedBlockStore(network, 1000)
    new FullPrunedBlockChain(network, blockStore)
  }

  override def wallet(peerGroup: PeerGroup) = {
    val walletFile = configProvider.bitcoinSettings().walletFile
    val wallet =
      if (walletFile.exists()) loadFromFile(walletFile)
      else emptyWalletAt(walletFile)
    setupWallet(peerGroup, wallet, walletFile)
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

  private def setupWallet(peerGroup: PeerGroup, wallet: SmartWallet, walletFile: File): Unit = {
    blockchain.addWallet(wallet.delegate)
    peerGroup.addWallet(wallet.delegate)
    wallet.delegate.autosaveToFile(walletFile, 250, TimeUnit.MILLISECONDS, null)
  }
}

object DefaultBitcoinComponents {
  private val Log = LoggerFactory.getLogger(classOf[DefaultBitcoinComponents])
}
