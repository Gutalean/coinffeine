package coinffeine.gui.application.launcher

import scala.util.Try

trait LaunchAction[A] {
  def apply(): Try[A]
}
