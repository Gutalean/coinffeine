package coinffeine.headless.shell

trait Command {
  val keyword: String
  def apply(args: String): Unit
}
