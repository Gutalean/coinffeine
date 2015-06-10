package coinffeine.headless.prompt

import scala.concurrent.ExecutionContext.Implicits.global

import coinffeine.common.properties.Property
import coinffeine.headless.prompt.ANSIText._
import coinffeine.headless.shell.Prompt
import coinffeine.model.bitcoin.BlockchainStatus
import coinffeine.model.bitcoin.BlockchainStatus.{Downloading, NotDownloading}
import coinffeine.peer.api.CoinffeineApp

class ConnectionStatusPrompt(connectionStatus: Property[PromptStatus])
  extends Prompt {

  def this(app: CoinffeineApp) = this(new PromptStatusProperty(app))

  override def value: String = {
    val status = connectionStatus.get
    Bold(coinffeineStatus(status), bitcoinStatus(status), "> ")
  }

  private def coinffeineStatus(status: PromptStatus): String = {
    val text =
      if (status.coinffeinePeers == 0) "No peers"
      else "%s:%d".format(if (status.knownBroker) "OK" else "Unknown broker", status.coinffeinePeers)
    val color = if (status.coinffeinePeers > 0 && status.knownBroker) Green else Red
    color(s"[C-$text]")
  }

  private def bitcoinStatus(status: PromptStatus): String = {
    val text =
      if (status.bitcoinPeers == 0) "No peers"
      else "%s:%d".format(bitcoinMessage(status.blockchainStatus), status.bitcoinPeers)
    val color = status.blockchainStatus match {
      case NotDownloading if status.bitcoinPeers > 0 => Green
      case NotDownloading => Red
      case Downloading(_, _) => Yellow
    }
    color(s"[B-$text]")
  }

  private def bitcoinMessage(status: BlockchainStatus): String = status match {
    case Downloading(total, remaining) if total > 0 =>
      "Downloading %d%%".format((total - remaining) * 100 / total)
    case _ => "OK"
  }
}
