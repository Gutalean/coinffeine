package coinffeine.headless.prompt

import coinffeine.common.properties.MutableProperty
import coinffeine.common.test.UnitTest
import coinffeine.headless.prompt.ANSIText._
import coinffeine.model.bitcoin.BlockchainStatus.{Downloading, NotDownloading}

class ConnectionStatusPromptTest extends UnitTest {

  val connectedStatus = PromptStatus(
    knownBroker = true,
    coinffeinePeers = 7,
    bitcoinPeers = 4,
    blockchainStatus = NotDownloading(lastBlock = None)
  )
  val property = new MutableProperty(connectedStatus)
  val prompt = new ConnectionStatusPrompt(property)

  "A connection status prompt" should "show OK and the number of peers when successfully connected" in {
    prompt.value shouldBe Bold(Green("[C-OK:7]"), Green("[B-OK:4]"), "> ")
  }

  it should "update the prompt as the property changes" in {
    property.set(connectedStatus.copy(coinffeinePeers = 6, bitcoinPeers = 5))
    prompt.value shouldBe Bold(Green("[C-OK:6]"), Green("[B-OK:5]"), "> ")
  }

  it should "notify when the broker is unknown" in {
    property.set(connectedStatus.copy(knownBroker = false))
    prompt.value shouldBe Bold(Red("[C-Unknown broker:7]"), Green("[B-OK:4]"), "> ")
  }

  it should "notify when there is no Coinffeine peers" in {
    property.set(connectedStatus.copy(coinffeinePeers = 0))
    prompt.value shouldBe Bold(Red("[C-No peers]"), Green("[B-OK:4]"), "> ")
  }

  it should "notify when there is no Bitcoin peers" in {
    property.set(connectedStatus.copy(bitcoinPeers = 0))
    prompt.value shouldBe Bold(Green("[C-OK:7]"), Red("[B-No peers]"), "> ")
  }

  it should "notify when there is a blockchain download" in {
    property.set(connectedStatus.copy(blockchainStatus = Downloading(
      totalBlocks = 100,
      remainingBlocks = 40
    )))
    prompt.value shouldBe Bold(Green("[C-OK:7]"), Yellow("[B-Downloading 60%:4]"), "> ")
  }
}
