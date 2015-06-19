package coinffeine.peer.bitcoin.platform

import org.bitcoinj.core.{AbstractBlockChain, PeerGroup}

import coinffeine.model.bitcoin.{Network, NetworkComponent}
import coinffeine.model.bitcoin.network.{IntegrationTestNetwork, MainNetwork, PublicTestNetwork}
import coinffeine.model.network.NetworkEndpoint
import coinffeine.peer.bitcoin.BitcoinSettings
import coinffeine.peer.bitcoin.wallet.SmartWallet
import coinffeine.peer.config.ConfigComponent

private class DefaultBitcoinPlatform(
  override val network: Network,
  override val blockchain: AbstractBlockChain,
  override val peerGroup: PeerGroup,
  override val wallet: SmartWallet,
  override val seedPeers: Seq[NetworkEndpoint]) extends BitcoinPlatform

object DefaultBitcoinPlatform {

  trait Component extends BitcoinPlatform.Component with NetworkComponent { this: ConfigComponent =>

    private lazy val networkSetting = configProvider.bitcoinSettings().network

    lazy val network = networkSetting match {
      case BitcoinSettings.IntegrationRegnet => IntegrationTestNetwork
      case BitcoinSettings.PublicTestnet => PublicTestNetwork
      case BitcoinSettings.MainNet => MainNetwork.network
    }

    override def bitcoinPlatform: BitcoinPlatform = {
      val settings = configProvider.bitcoinSettings()
      new DefaultBitcoinPlatformBuilder()
        .setNetwork(network)
        .setWalletFile(settings.walletFile)
        .setBlockchainFile(settings.blockchainFile)
        .enableSpv(settings.spv)
        .setCheckpoints(Option(getClass.getResource(s"/${networkSetting.name}.checkpoints")))
        .build()
    }
  }
}
