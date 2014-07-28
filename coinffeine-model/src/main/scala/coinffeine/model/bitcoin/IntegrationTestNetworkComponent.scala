package coinffeine.model.bitcoin

import java.net.InetAddress

import com.google.bitcoin.core.{AbstractBlockChain, PeerAddress, PeerGroup}
import com.google.bitcoin.params.TestNet3Params

trait IntegrationTestNetworkComponent extends NetworkComponent with PeerGroupComponent {

  override lazy val network = new TestNet3Params() {
    dnsSeeds = Array.empty
  }

  override def createPeerGroup(blockchain: AbstractBlockChain): PeerGroup = {
    val group = new PeerGroup(network, blockchain)
    group.addAddress(testnetAddress())
    group
  }

  private def testnetAddress() = new PeerAddress(
    InetAddress.getByName(IntegrationTestNetworkComponent.Hostname),
    IntegrationTestNetworkComponent.Port
  )
}

object IntegrationTestNetworkComponent {
  val Port = 19000
  val Hostname = "testnet.test.coinffeine.com"
}
