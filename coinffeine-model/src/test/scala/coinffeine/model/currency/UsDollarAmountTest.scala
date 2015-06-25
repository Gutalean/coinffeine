package coinffeine.model.currency

import coinffeine.common.test.UnitTest

class UsDollarAmountTest extends UnitTest with CurrencyAmountBehaviors {

   "US dollar amount" should behave like aCurrencyAmount(UsDollar)

   it should "be printable" in {
     UsDollar.zero.toString shouldBe "0.00USD"
     0.01.USD.toString shouldBe "0.01USD"
   }
 }
