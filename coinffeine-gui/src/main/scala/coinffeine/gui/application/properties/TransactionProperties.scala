package coinffeine.gui.application.properties

import java.util.Date
import scala.collection.JavaConversions._

import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}

import com.google.bitcoin.core.Sha256Hash

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.currency.Currency.Bitcoin

class TransactionProperties(tx: ImmutableTransaction) {

  private val _time = new ObjectProperty(this, "time", tx.get.getUpdateTime)
  private val _hash = new ObjectProperty(this, "hash", tx.get.getHash)

  val time: ReadOnlyObjectProperty[Date] = _time
  val hash: ReadOnlyObjectProperty[Sha256Hash] = _hash

  def update(tx: ImmutableTransaction): Unit = {
    _time.value = tx.get.getUpdateTime
    _hash.value = tx.get.getHash
  }

  def hasHash(h: Sha256Hash): Boolean = hash.value == h
}
