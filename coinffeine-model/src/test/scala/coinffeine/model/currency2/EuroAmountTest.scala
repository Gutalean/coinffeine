package coinffeine.model.currency2

import coinffeine.common.test.UnitTest

class EuroAmountTest extends UnitTest with CurrencyAmountBehaviors {

   "Euro amount" should behave like aCurrencyAmount(Euro)

   it should "be printable" in {
     Euro.zero.toString shouldBe "0.00EUR"
     0.01.EUR.toString shouldBe "0.01EUR"
   }
 }
