package coinffeine.model.bitcoin.network

import java.net.InetAddress
import scala.util.Try

import org.bitcoinj.core.PeerAddress
import org.bitcoinj.params.TestNet3Params

import coinffeine.model.bitcoin.NetworkComponent

object IntegrationTestNetwork {
  val Port = 19000
  val Hostname = "testnet.test.coinffeine.com"
  val NetworkParams = new TestNet3Params() {
    dnsSeeds = Array.empty
  }

  trait Component extends NetworkComponent {
    override def network = NetworkParams
    override def peerAddresses = testnetAddress().toSeq

    def testnetAddress(): Option[PeerAddress] = Try(new PeerAddress(
      InetAddress.getByName(IntegrationTestNetwork.Hostname),
      IntegrationTestNetwork.Port
    )).toOption
  }
}
