package coinffeine.headless.commands

import java.io.PrintWriter

import coinffeine.headless.shell.Command
import coinffeine.model.currency.BitcoinBalance
import coinffeine.peer.api.{CoinffeinePaymentProcessor, CoinffeineApp}

class StatusCommand(app: CoinffeineApp) extends Command {

  override val keyword = "status"

  override def apply(output: PrintWriter, args: String): Unit = {
    output.format("FIAT: %s%n", app.paymentProcessor.currentBalance().fold("--")(formatFiatBalance))
    output.format("BTC: %s%n", app.wallet.balance.get.fold("--")(formatBitcoinBalance))
  }

  private def formatFiatBalance(balance: CoinffeinePaymentProcessor.Balance): String = {
    val blockedFunds =
      if (balance.blockedFunds.isPositive) s" (${balance.blockedFunds} blocked)" else ""
    balance.totalFunds + blockedFunds
  }

  private def formatBitcoinBalance(balance: BitcoinBalance): String = {
    val blocked = if (balance.blocked.isPositive) s" (${balance.blocked} blocked)" else ""
    "%s estimated, %s available%s".format(balance.estimated, balance.available, blocked)
  }
}
