package coinffeine.peer.bitcoin

import java.util.concurrent.TimeUnit

import com.google.bitcoin.core.{AbstractBlockChain, FullPrunedBlockChain, PeerGroup}
import com.google.bitcoin.store.MemoryFullPrunedBlockStore
import org.slf4j.LoggerFactory

import coinffeine.model.bitcoin.Implicits._
import coinffeine.model.bitcoin._
import coinffeine.peer.config.ConfigComponent

trait DefaultBitcoinComponents
    extends PeerGroupComponent with BlockchainComponent with WalletComponent {
  this: NetworkComponent with ConfigComponent =>

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
    val wallet = new Wallet(network)
    val walletFile = configProvider.bitcoinSettings.walletFile
    if (walletFile.exists()) {
      DefaultBitcoinComponents.Log.info("Loading wallet from {}", walletFile)
      wallet.loadFromFile(walletFile)
    } else {
      DefaultBitcoinComponents.Log.warn("{} doesn't exists, starting with an empty wallet", walletFile)
    }
    blockchain.addWallet(wallet)
    peerGroup.addWallet(wallet)
    wallet.autosaveToFile(walletFile, 250, TimeUnit.MILLISECONDS, null)
    wallet
  }
}

object DefaultBitcoinComponents {
  private val Log = LoggerFactory.getLogger(classOf[DefaultBitcoinComponents])
}
