package coinffeine.peer.appdata

import scalaz.\/
import scalaz.syntax.all._

import coinffeine.peer.config.ConfigProvider

trait Migration {
  def apply(context: Migration.Context): Migration.Result
}

object Migration {

  type Result = Error \/ Unit
  val Success: Result = ().right

  sealed trait Error
  case object Aborted extends Error
  case class Failed(cause: Throwable) extends Error

  trait Context {
    val config: ConfigProvider
    def confirm(title: String, question: String): Boolean
  }
}
