package coinffeine.peer.api.impl

import coinffeine.peer.amounts.ExchangeAmountsCalculator
import coinffeine.peer.api.CoinffeineUtils

class DefaultCoinffeineUtils(
  override val exchangeAmountsCalculator: ExchangeAmountsCalculator
) extends CoinffeineUtils
