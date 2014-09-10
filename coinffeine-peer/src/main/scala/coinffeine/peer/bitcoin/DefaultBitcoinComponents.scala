package coinffeine.peer.bitcoin

import java.io.File
import java.util.concurrent.TimeUnit

import com.google.bitcoin.core.{AbstractBlockChain, FullPrunedBlockChain, PeerGroup}
import com.google.bitcoin.store.MemoryFullPrunedBlockStore

import coinffeine.model.bitcoin.Implicits._
import coinffeine.model.bitcoin._
import coinffeine.peer.config.user.LocalAppDataDir

trait DefaultBitcoinComponents
    extends PeerGroupComponent with BlockchainComponent with WalletComponent {

  this: NetworkComponent =>

  import coinffeine.peer.bitcoin.DefaultBitcoinComponents._

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
    if (UserWalletFile.exists()) {
      wallet.loadFromFile(UserWalletFile)
    }
    blockchain.addWallet(wallet)
    peerGroup.addWallet(wallet)
    wallet.autosaveToFile(UserWalletFile, 250, TimeUnit.MILLISECONDS, null)
    wallet

  }
}

object DefaultBitcoinComponents {

  lazy val UserWalletFile: File = LocalAppDataDir.getFile("user.wallet", false).toFile
}
