package coinffeine.model.bitcoin.network

import java.net.InetAddress
import scala.util.Try

import org.bitcoinj.core.PeerAddress
import org.bitcoinj.params.TestNet3Params

import coinffeine.model.bitcoin.NetworkComponent

trait TestNetwork {
  val Hostname: String
  val Port = 19000
  val NetworkParams = new TestNet3Params() {
    dnsSeeds = Array.empty
  }

  trait Component extends NetworkComponent {
    override def network = NetworkParams
    override def peerAddresses = testnetAddress().toSeq

    def testnetAddress(): Option[PeerAddress] =
      Try(new PeerAddress(InetAddress.getByName(Hostname), Port)).toOption
  }
}

object IntegrationTestNetwork extends TestNetwork {
  val Hostname = "testnet.test.coinffeine.com"
}

object PublicTestNetwork extends TestNetwork {
  val Hostname = "prod.coinffeine.com"
}
