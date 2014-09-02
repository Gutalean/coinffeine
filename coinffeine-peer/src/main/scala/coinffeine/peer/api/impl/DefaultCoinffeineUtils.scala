package coinffeine.peer.api.impl

import coinffeine.peer.amounts.AmountsCalculator
import coinffeine.peer.api.CoinffeineUtils

class DefaultCoinffeineUtils(
  override val exchangeAmountsCalculator: AmountsCalculator
) extends CoinffeineUtils
