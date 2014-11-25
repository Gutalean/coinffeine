package coinffeine.headless.prompt

import scala.concurrent.ExecutionContext.Implicits.global

import coinffeine.headless.shell.Prompt
import coinffeine.model.bitcoin.BlockchainStatus
import coinffeine.model.bitcoin.BlockchainStatus.Downloading
import coinffeine.model.properties.Property
import coinffeine.peer.api.CoinffeineApp

class ConnectionStatusPrompt(connectionStatus: Property[PromptStatus])
  extends Prompt {

  def this(app: CoinffeineApp) = this(new PromptStatusProperty(app))

  override def value: String = {
    val status = connectionStatus.get
    s"[C-${coinffeineStatus(status)}][B-${bitcoinStatus(status)}]> "
  }

  private def coinffeineStatus(status: PromptStatus): String =
    if (status.coinffeinePeers == 0) "No peers"
    else "%s:%d".format(if (status.knownBroker) "OK" else "Unknown broker", status.coinffeinePeers)

  private def bitcoinStatus(status: PromptStatus): String =
    if (status.bitcoinPeers == 0) "No peers"
    else "%s:%d".format(bitcoinMessage(status.blockchainStatus), status.bitcoinPeers)

  private def bitcoinMessage(status: BlockchainStatus): String = status match {
    case Downloading(total, remaining) if total > 0 =>
      "Downloading %d%%".format((total - remaining) * 100 / total)
    case _ => "OK"
  }
}
