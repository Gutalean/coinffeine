package coinffeine.peer.api.impl

import coinffeine.model.bitcoin.BitcoinFeeCalculator
import coinffeine.peer.amounts.AmountsCalculator
import coinffeine.peer.api.CoinffeineUtils

class DefaultCoinffeineUtils(
  override val exchangeAmountsCalculator: AmountsCalculator,
  override val bitcoinFeeCalculator: BitcoinFeeCalculator
) extends CoinffeineUtils
