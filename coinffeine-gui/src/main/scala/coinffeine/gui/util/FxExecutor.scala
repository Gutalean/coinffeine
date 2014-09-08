package coinffeine.gui.util

import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext
import scalafx.application.Platform

object FxExecutor extends Executor {

  implicit def asContext: ExecutionContext = ExecutionContext.fromExecutor(this)

  override def execute(command: Runnable) = Platform.runLater(command)
}
