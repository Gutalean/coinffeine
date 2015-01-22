package coinffeine.peer.bitcoin.platform

import org.bitcoinj.core.{AbstractBlockChain, PeerGroup}

import coinffeine.model.bitcoin.NetworkComponent
import coinffeine.model.bitcoin.network.{IntegrationTestNetwork, MainNetwork, PublicTestNetwork}
import coinffeine.model.network.NetworkEndpoint
import coinffeine.peer.bitcoin.BitcoinSettings
import coinffeine.peer.bitcoin.wallet.SmartWallet
import coinffeine.peer.config.ConfigComponent

private class DefaultBitcoinPlatform(val blockchain: AbstractBlockChain,
                                     val peerGroup: PeerGroup,
                                     val wallet: SmartWallet,
                                     val seedPeers: Seq[NetworkEndpoint]) extends BitcoinPlatform

object DefaultBitcoinPlatform {

  trait Component extends BitcoinPlatform.Component with NetworkComponent { this: ConfigComponent =>

    lazy val network = configProvider.bitcoinSettings().network match {
      case BitcoinSettings.IntegrationTestnet => IntegrationTestNetwork
      case BitcoinSettings.PublicTestnet => PublicTestNetwork
      case BitcoinSettings.MainNet => MainNetwork.network
    }

    override def bitcoinPlatformBuilder: BitcoinPlatform.Builder = {
      val settings = configProvider.bitcoinSettings()
      new DefaultBitcoinPlatformBuilder()
        .setNetwork(network)
        .setWalletFile(settings.walletFile)
        .setBlockchainFile(settings.blockchainFile)
    }
  }
}
