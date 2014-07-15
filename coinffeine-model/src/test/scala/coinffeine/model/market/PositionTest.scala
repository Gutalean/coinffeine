package coinffeine.model.market

import coinffeine.model.currency.Implicits._
import coinffeine.model.network.PeerId
import com.coinffeine.common.test.UnitTest

class PositionTest extends UnitTest {

  "A position" should "be folded depending on its type" in  {
    val id = PositionId(PeerId("user"), OrderId("order"))
    Position.bid(1.BTC, 100.EUR, id).fold(
      bid = p => p.amount,
      ask = p => -p.amount
    ) should be (1.BTC)
    Position.ask(1.BTC, 100.EUR, id).fold(
      bid = p => p.amount,
      ask = p => -p.amount
    ) should be (-1.BTC)
  }
}
