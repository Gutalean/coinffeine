package coinffeine.peer.api

import coinffeine.peer.amounts.AmountsCalculator

trait CoinffeineUtils {

  def exchangeAmountsCalculator: AmountsCalculator
}
