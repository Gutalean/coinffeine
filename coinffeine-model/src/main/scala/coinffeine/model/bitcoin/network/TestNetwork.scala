package coinffeine.model.bitcoin.network

import java.net.InetAddress
import scala.util.Try

import org.bitcoinj.core.PeerAddress
import org.bitcoinj.params.TestNet3Params

import coinffeine.model.bitcoin.NetworkComponent

trait TestNetwork {
  val Hostname: String
  val Ports: Seq[Int]
  val NetworkParams = new TestNet3Params() {
    dnsSeeds = Array.empty
  }

  trait Component extends NetworkComponent {
    override def network = NetworkParams
    override def peerAddresses = testnetAddresses()

    def testnetAddresses(): Seq[PeerAddress] = for {
      address <- Try(InetAddress.getByName(Hostname)).toOption.toSeq
      port <- Ports
    } yield new PeerAddress(address, port)
  }
}

object IntegrationTestNetwork extends TestNetwork {
  val Hostname = "testnet.test.coinffeine.com"
  val Ports = Seq(19000)
}

object PublicTestNetwork extends TestNetwork {
  val Hostname = "testnet.trial.coinffeine.com"
  val Ports = 19000 to 19004 toSeq
}
