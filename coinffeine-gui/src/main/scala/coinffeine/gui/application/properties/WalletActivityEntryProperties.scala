package coinffeine.gui.application.properties

import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}

import org.joda.time.DateTime

import coinffeine.model.bitcoin.{Hash, WalletActivity}

class WalletActivityEntryProperties(initialEntry: WalletActivity.Entry) {

  private val _entry = new ObjectProperty(this, "entry", initialEntry)
  private val _time = new ObjectProperty(this, "time", initialEntry.time)
  private val _hash = new ObjectProperty(this, "hash", initialEntry.tx.get.getHash)

  val entry: ReadOnlyObjectProperty[WalletActivity.Entry] = _entry
  val time: ReadOnlyObjectProperty[DateTime] = _time
  val hash: ReadOnlyObjectProperty[Hash] = _hash

  def update(entry: WalletActivity.Entry): Unit = {
    _entry.value = entry
    _time.value = entry.time
    _hash.value = entry.tx.get.getHash
  }

  def hasHash(h: Hash): Boolean = hash.value == h
}
