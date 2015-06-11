package coinffeine.headless.commands

import java.io.PrintWriter

import coinffeine.headless.prompt.ANSIText.Red
import coinffeine.headless.shell.Command
import coinffeine.peer.api.CoinffeineOperations

class CancelOrderCommand(operations: CoinffeineOperations) extends Command {

  override val keyword = "cancel"
  override val description = "cancels an order"
  override val usage =
    """cancel <order id>
      |
      |  where order id is an UUID similar to 0bb816cc-2acc-443e-a7e7-7b54a07e2d9c
    """.stripMargin

  private val validator = new OrderIdValidation(operations)

  override def apply(output: PrintWriter, args: String): Unit = {
    def reportError(message: String): Unit = {
      output.println(Red("ERROR: " + message))
    }
    validator.requireExistingOrderId(args).fold(fail = reportError, succ = operations.cancelOrder)
  }
}
