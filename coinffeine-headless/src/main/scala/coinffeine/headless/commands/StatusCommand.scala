package coinffeine.headless.commands

import java.io.PrintWriter

import coinffeine.headless.shell.Command
import coinffeine.model.currency.balance.{BitcoinBalance, FiatBalance}
import coinffeine.peer.api.CoinffeineApp

class StatusCommand(app: CoinffeineApp) extends Command {

  override val keyword = "status"
  override val description = "general status, i.e. bitcoin/fiat balances and addresses"

  override def apply(output: PrintWriter, args: String): Unit = {
    printBalances(output)
    printWalletAddress(output)
  }

  private def printBalances(output: PrintWriter): Unit = {
    output.format("FIAT: %s%n", app.paymentProcessor.currentBalance().fold("--")(formatFiatBalance))
    output.format("BTC: %s%n", app.wallet.balance.get.fold("--")(formatBitcoinBalance))
  }

  private def printWalletAddress(output: PrintWriter): Unit = {
    output.format("Wallet address: %s%n", app.wallet.primaryAddress.get.getOrElse("--"))
  }

  private def formatFiatBalance(balance: FiatBalance): String = {
    val blockedFunds =
      if (balance.blockedAmount.isPositive) s" (${balance.blockedAmount} blocked)" else ""
    balance.amount + blockedFunds
  }

  private def formatBitcoinBalance(balance: BitcoinBalance): String = {
    val details =
      (if (balance.blocked.isPositive) Some(s"${balance.blocked} blocked") else None) ++
        balance.minOutput.map(value => s"min output of $value")
    val formattedDetails =
      if (details.isEmpty) ""
      else details.mkString(" (", ", ", ")")
    "%s estimated, %s available%s".format(balance.estimated, balance.available, formattedDetails)
  }
}
