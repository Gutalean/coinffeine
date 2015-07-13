package coinffeine.gui.application.properties

import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}

import org.joda.time.DateTime

import coinffeine.gui.application.wallet.WalletEntryView
import coinffeine.model.bitcoin.WalletActivity

class WalletActivityEntryProperties(initialEntry: WalletActivity.Entry) {

  private val _entry = new ObjectProperty(this, "entry", initialEntry)
  private val _view = new ObjectProperty(this, "view", WalletEntryView.forEntry(initialEntry))
  private val _time = new ObjectProperty(this, "time", initialEntry.time)

  val entry: ReadOnlyObjectProperty[WalletActivity.Entry] = _entry
  val view: ReadOnlyObjectProperty[WalletEntryView] = _view
  val time: ReadOnlyObjectProperty[DateTime] = _time

  def update(entry: WalletActivity.Entry): Unit = {
    _entry.value = entry
    _view.value = WalletEntryView.forEntry(entry)
    _time.value = entry.time
  }
}
