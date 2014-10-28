package coinffeine.gui.control

import coinffeine.common.test.UnitTest
import coinffeine.gui.control.ConnectionStatus.{Red, Yellow, Green}
import coinffeine.model.bitcoin.BlockchainStatus
import coinffeine.model.network.PeerId

class ConnectionStatusTest extends UnitTest {

  val bitcoinStatuses = Set(
    ConnectionStatus.Bitcoin(0, BlockchainStatus.NotDownloading),
    ConnectionStatus.Bitcoin(0, BlockchainStatus.Downloading(100, 50)),
    ConnectionStatus.Bitcoin(1, BlockchainStatus.NotDownloading),
    ConnectionStatus.Bitcoin(1, BlockchainStatus.Downloading(100, 50)),
    ConnectionStatus.Bitcoin(2, BlockchainStatus.NotDownloading),
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
  } yield ConnectionStatus(coinffeineStatus, bitcoinStatus)

  "A combined connection status" should
    "have color green when connected to both networks and not downloading blocks" in {
      for {
        coinffeineStatus <- coinffeineStatuses if coinffeineStatus.connected
        bitcoinStatus <- bitcoinStatuses
        if bitcoinStatus.connected && bitcoinStatus.blockchainStatus == BlockchainStatus.NotDownloading
        combinedStatus = ConnectionStatus(coinffeineStatus, bitcoinStatus)
      } withClue(combinedStatus) {
        combinedStatus.color should be (Green)
      }
    }

  it should "have color yellow when connected to both networks and downloading blocks" in {
    for {
      coinffeineStatus <- coinffeineStatuses if coinffeineStatus.connected
      bitcoinStatus <- bitcoinStatuses
      if bitcoinStatus.connected && bitcoinStatus.blockchainStatus != BlockchainStatus.NotDownloading
      combinedStatus = ConnectionStatus(coinffeineStatus, bitcoinStatus)
    } withClue(combinedStatus) {
      combinedStatus.color should be (Yellow)
    }
  }

  it should "have color red when disconnected from the coinffeine network" in {
    for {
      status <- anyStatus if !status.coinffeine.connected
    } withClue(status) {
      status.color should be (Red)
    }
  }

  it should "have color red when disconnected from the bitcoin network" in {
    for {
      status <- anyStatus if !status.bitcoin.connected
    } withClue(status) {
      status.color should be (Red)
    }
  }

  it should "report the number of coinffeine connected peers" in {
    forEveryStatus { status =>
      status.description should include (s"${status.coinffeine.activePeers} coinffeine peer")
    }
  }

  it should "report the number of bitcoin connected peers" in {
    forEveryStatus { status =>
      status.description should include (s"${status.bitcoin.activePeers} bitcoin peer")
    }
  }

  it should "report the blockchain syncing progress" in {
    forEveryStatus { status =>
      if (status.bitcoin.blockchainStatus == BlockchainStatus.NotDownloading) {
        status.description should not include "syncing blockchain"
      } else {
        status.description should include("syncing blockchain")
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
