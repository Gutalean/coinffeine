package coinffeine.gui.application.properties

import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}

import org.joda.time.DateTime

import coinffeine.model.bitcoin.{Hash, WalletActivity}
import coinffeine.model.currency.Bitcoin

class WalletActivityEntryProperties(entry: WalletActivity.Entry) {

  private val _time = new ObjectProperty(this, "time", entry.time)
  private val _hash = new ObjectProperty(this, "hash", entry.tx.get.getHash)
  private val _amount = new ObjectProperty(this, "hash", entry.amount)

  val time: ReadOnlyObjectProperty[DateTime] = _time
  val hash: ReadOnlyObjectProperty[Hash] = _hash
  val amount: ReadOnlyObjectProperty[Bitcoin.Amount] = _amount

  def update(entry: WalletActivity.Entry): Unit = {
    _time.value = entry.time
    _hash.value = entry.tx.get.getHash
    _amount.value = entry.amount
  }

  def hasHash(h: Hash): Boolean = hash.value == h
}
