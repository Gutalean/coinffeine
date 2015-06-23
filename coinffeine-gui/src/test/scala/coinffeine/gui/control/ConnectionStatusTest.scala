package coinffeine.gui.control

import org.joda.time.DateTime

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.BlockchainStatus
import coinffeine.model.network.PeerId

class ConnectionStatusTest extends UnitTest {

  val bitcoinStatuses = Set(
    ConnectionStatus.Bitcoin(0, BlockchainStatus.NotDownloading(lastBlock = None)),
    ConnectionStatus.Bitcoin(0, BlockchainStatus.Downloading(100, 50)),
    ConnectionStatus.Bitcoin(1, BlockchainStatus.NotDownloading(lastBlock = None)),
    ConnectionStatus.Bitcoin(1, BlockchainStatus.Downloading(100, 50)),
    ConnectionStatus.Bitcoin(2, BlockchainStatus.NotDownloading(
      lastBlock = Some(BlockchainStatus.BlockInfo(100, DateTime.now())))),
    ConnectionStatus.Bitcoin(3, BlockchainStatus.Downloading(100, 10))
  )
  val coinffeineStatuses = Set(
    ConnectionStatus.Coinffeine(0),
    ConnectionStatus.Coinffeine(1),
    ConnectionStatus.Coinffeine(2),
    ConnectionStatus.Coinffeine(1, brokerId = Some(PeerId.hashOf("broker"))),
    ConnectionStatus.Coinffeine(2, brokerId = Some(PeerId.hashOf("broker")))
  )
  val anyStatus = for {
    coinffeineStatus <- coinffeineStatuses
    bitcoinStatus <- bitcoinStatuses
  } yield ConnectionStatus(coinffeineStatus, bitcoinStatus, now = DateTime.now())

  "A combined connection status" should "report the number of coinffeine connected peers" in {
    forEveryStatus { status =>
      status.description should include (s"${status.coinffeine.activePeers} coinffeine peer")
    }
  }

  it should "report the number of bitcoin connected peers" in {
    forEveryStatus { status =>
      status.description should include (s"${status.bitcoin.activePeers} bitcoin peer")
    }
  }

  it should "report the blockchain details" in {
    forEveryStatus { status =>
      status.bitcoin.blockchainStatus match {
        case BlockchainStatus.NotDownloading(None) =>
          status.description should not include "last block"
          status.description should not include "syncing blockchain"
        case BlockchainStatus.NotDownloading(Some(_)) =>
          status.description should include ("last block")
          status.description should not include "syncing blockchain"
        case _ =>
          status.description should not include "last block"
          status.description should include ("syncing blockchain")
      }
    }
  }

  private def forEveryStatus(assertOnStatus: ConnectionStatus => Unit): Unit = {
    for {
      status <- anyStatus
    } withClue(status) {
      assertOnStatus(status)
    }
  }
}
