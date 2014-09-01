package coinffeine.peer.api

import coinffeine.peer.amounts.ExchangeAmountsCalculator

trait CoinffeineUtils {

  def exchangeAmountsCalculator: ExchangeAmountsCalculator
}
