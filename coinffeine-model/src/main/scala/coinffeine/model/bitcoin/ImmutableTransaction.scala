package coinffeine.model.bitcoin

import scala.util.control.NonFatal

/** Wrapper for bitcoinj transactions.
  *
  * As bitcoinj transactions are mutable we need a source of fresh objects to keep our sanity.
  * This class adds some niceties such as a proper string conversion an a syntax similar to
  * {{{Future { ... } }}}.
  */
class ImmutableTransaction(private val bytes: Array[Byte], private val network: Network) {

  def this(tx: MutableTransaction) = this(tx.bitcoinSerialize(), tx.getParams)

  override lazy val toString: String = {
    val string = try {
      get.toString
    } catch {
      case NonFatal(ex) => s"Cannot format as string (${ex.getMessage}})"
    }
    s"ImmutableTransaction($string)"
  }

  def get: MutableTransaction = new MutableTransaction(network, bytes)

  override def equals(other: Any): Boolean = other match {
    case that: ImmutableTransaction => bytes.sameElements(that.bytes) && network == that.network
    case _ => false
  }

  override def hashCode(): Int =
    bytes.foldLeft(network.hashCode())((accum, elem) => 31 * accum + elem.hashCode())
}

object ImmutableTransaction {
  def apply(tx: MutableTransaction) = new ImmutableTransaction(tx)
}
