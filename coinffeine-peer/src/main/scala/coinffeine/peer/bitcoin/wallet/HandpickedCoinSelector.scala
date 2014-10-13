package coinffeine.peer.bitcoin.wallet

import scala.collection.JavaConversions._

import org.bitcoinj.core.Coin
import org.bitcoinj.wallet.{CoinSelection, CoinSelector}

import coinffeine.model.bitcoin.MutableTransactionOutput

/** Coin selector choosing outputs from a handpicked set */
private class HandpickedCoinSelector(eligibleOutputs: Set[MutableTransactionOutput])
  extends CoinSelector {

  override def select(amount: Coin, candidates: java.util.List[MutableTransactionOutput]) = {
    var selectedValue = Coin.ZERO
    val selectedOutputs = candidates.toStream
      .filter(eligibleOutputs.contains)
      .takeWhile(_ => selectedValue.isLessThan(amount))
      .map { candidate =>
        selectedValue = selectedValue.add(candidate.getValue)
        candidate
      }
      .toList
    new CoinSelection(selectedValue, selectedOutputs)
  }
}
