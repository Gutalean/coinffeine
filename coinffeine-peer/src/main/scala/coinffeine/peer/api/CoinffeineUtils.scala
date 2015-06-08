package coinffeine.peer.api

import coinffeine.model.bitcoin.BitcoinFeeCalculator
import coinffeine.peer.amounts.AmountsCalculator

trait CoinffeineUtils {

  def exchangeAmountsCalculator: AmountsCalculator
  def bitcoinFeeCalculator: BitcoinFeeCalculator
}
