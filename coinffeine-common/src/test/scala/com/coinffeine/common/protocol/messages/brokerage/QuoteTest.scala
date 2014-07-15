package com.coinffeine.common.protocol.messages.brokerage

import coinffeine.model.currency.Currency
import Currency.Euro
import coinffeine.model.currency.Implicits._
import com.coinffeine.common.test.UnitTest

class QuoteTest extends UnitTest {

  "A quote" must "print to a readable string" in {
    Quote(10.EUR -> 20.EUR, 15 EUR).toString should
      be ("Quote(spread = (10 EUR, 20 EUR), last = 15 EUR)")
    Quote(Market(Euro), Some(10 EUR) -> None, None).toString should
      be ("Quote(spread = (10 EUR, --), last = --)")
  }
}
