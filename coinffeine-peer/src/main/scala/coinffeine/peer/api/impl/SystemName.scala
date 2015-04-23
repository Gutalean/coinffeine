package coinffeine.peer.api.impl

import scala.util.Random

/** Policy for choosing the actor system name */
private object SystemName {

  /** Choose a name optionally based on a hint string
    *
    * @param hint  String used as a base to create the system name (can contain invalid characters)
    * @return      A valid name containing only alphanumeric characters and dashes.
    */
  def choose(hint: Option[String]) = hint.fold(randomName())(derivedName)

  private def derivedName(account: String): String =
    Prefix + account.replaceAll(InvalidCharacters, "_")

  private def randomName(): String = Prefix + Random.nextInt(1000)

  private val Prefix = "app-"
  private val InvalidCharacters = "[^a-zA-Z0-9_-]"
}
