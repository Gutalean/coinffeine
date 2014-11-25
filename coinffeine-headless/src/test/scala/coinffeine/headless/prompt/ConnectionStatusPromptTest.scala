package coinffeine.headless.prompt

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.BlockchainStatus.{Downloading, NotDownloading}
import coinffeine.model.properties.MutableProperty

class ConnectionStatusPromptTest extends UnitTest {

  val connectedStatus = PromptStatus(
    knownBroker = true,
    coinffeinePeers = 7,
    bitcoinPeers = 4,
    blockchainStatus = NotDownloading
  )
  val property = new MutableProperty(connectedStatus)
  val prompt = new ConnectionStatusPrompt(property)

  "A connection status prompt" should "show OK and the number of peers when successfully connected" in {
    prompt.value shouldBe "[C-OK:7][B-OK:4]> "
  }

  it should "update the prompt as the property changes" in {
    property.set(connectedStatus.copy(coinffeinePeers = 6, bitcoinPeers = 5))
    prompt.value shouldBe "[C-OK:6][B-OK:5]> "
  }

  it should "notify when the broker is unknown" in {
    property.set(connectedStatus.copy(knownBroker = false))
    prompt.value shouldBe "[C-Unknown broker:7][B-OK:4]> "
  }

  it should "notify when there is no Coinffeine peers" in {
    property.set(connectedStatus.copy(coinffeinePeers = 0))
    prompt.value shouldBe "[C-No peers][B-OK:4]> "
  }

  it should "notify when there is no Bitcoin peers" in {
    property.set(connectedStatus.copy(bitcoinPeers = 0))
    prompt.value shouldBe "[C-OK:7][B-No peers]> "
  }

  it should "notify when there is a blockchain download" in {
    property.set(connectedStatus.copy(blockchainStatus = Downloading(
      totalBlocks = 100,
      remainingBlocks = 40
    )))
    prompt.value shouldBe "[C-OK:7][B-Downloading 60%:4]> "
  }
}
