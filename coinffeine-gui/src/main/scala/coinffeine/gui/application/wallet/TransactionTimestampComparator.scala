package coinffeine.gui.application.wallet

import java.util.Comparator

import coinffeine.gui.application.properties.WalletActivityEntryProperties

class TransactionTimestampComparator extends Comparator[WalletActivityEntryProperties] {

  override def compare(left: WalletActivityEntryProperties,
                       right: WalletActivityEntryProperties): Int =
    right.time.value.compareTo(left.time.value)
}

