package coinffeine.gui.control

import coinffeine.common.test.UnitTest
import coinffeine.gui.control.CombinedConnectionStatus.{Red, Yellow, Green}
import coinffeine.model.event.{CoinffeineConnectionStatus, BitcoinConnectionStatus}
import coinffeine.model.event.BitcoinConnectionStatus.{Downloading, NotDownloading}
import coinffeine.model.network.PeerId

class CombinedConnectionStatusTest extends UnitTest {

  val bitcoinStatuses = Set(
    BitcoinConnectionStatus(0, NotDownloading),
    BitcoinConnectionStatus(0, Downloading(100, 50)),
    BitcoinConnectionStatus(1, NotDownloading),
    BitcoinConnectionStatus(1, Downloading(100, 50)),
    BitcoinConnectionStatus(2, NotDownloading),
    BitcoinConnectionStatus(3, Downloading(100, 10))
  )
  val coinffeineStatuses = Set(
    CoinffeineConnectionStatus(0),
    CoinffeineConnectionStatus(1),
    CoinffeineConnectionStatus(2),
    CoinffeineConnectionStatus(1, brokerId = Some(PeerId("broker"))),
    CoinffeineConnectionStatus(2, brokerId = Some(PeerId("broker")))
  )
  val anyStatus = for {
    coinffeineStatus <- coinffeineStatuses
    bitcoinStatus <- bitcoinStatuses
  } yield CombinedConnectionStatus(coinffeineStatus, bitcoinStatus)

  "A combined connection status" should
    "have color green when connected to both networks and not downloading blocks" in {
      for {
        coinffeineStatus <- coinffeineStatuses if coinffeineStatus.connected
        bitcoinStatus <- bitcoinStatuses
        if bitcoinStatus.connected && bitcoinStatus.blockchainStatus == NotDownloading
        combinedStatus = CombinedConnectionStatus(coinffeineStatus, bitcoinStatus)
      } withClue(combinedStatus) {
        combinedStatus.color should be (Green)
      }
    }

  it should "have color yellow when connected to both networks and downloading blocks" in {
    for {
      coinffeineStatus <- coinffeineStatuses if coinffeineStatus.connected
      bitcoinStatus <- bitcoinStatuses
      if bitcoinStatus.connected && bitcoinStatus.blockchainStatus != NotDownloading
      combinedStatus = CombinedConnectionStatus(coinffeineStatus, bitcoinStatus)
    } withClue(combinedStatus) {
      combinedStatus.color should be (Yellow)
    }
  }

  it should "have color red when disconnected from the coinffeine network" in {
    for {
      status <- anyStatus if !status.coinffeineStatus.connected
    } withClue(status) {
      status.color should be (Red)
    }
  }

  it should "have color red when disconnected from the bitcoin network" in {
    for {
      status <- anyStatus if !status.bitcoinStatus.connected
    } withClue(status) {
      status.color should be (Red)
    }
  }

  it should "report the number of coinffeine connected peers" in {
    forEveryStatus { status =>
      status.description should include (s"${status.coinffeineStatus.activePeers} coinffeine peer")
    }
  }

  it should "report the number of bitcoin connected peers" in {
    forEveryStatus { status =>
      status.description should include (s"${status.bitcoinStatus.activePeers} bitcoin peer")
    }
  }

  it should "report the blockchain syncing progress" in {
    forEveryStatus { status =>
      if (status.bitcoinStatus.blockchainStatus == NotDownloading) {
        status.description should not include "syncing blockchain"
      } else {
        status.description should include("syncing blockchain")
      }
    }
  }

  private def forEveryStatus(assertOnStatus: CombinedConnectionStatus => Unit): Unit = {
    for {
      status <- anyStatus
    } withClue(status) {
      assertOnStatus(status)
    }
  }
}
