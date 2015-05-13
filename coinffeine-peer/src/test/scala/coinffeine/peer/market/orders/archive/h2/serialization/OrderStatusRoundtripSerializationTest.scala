package coinffeine.peer.market.orders.archive.h2.serialization

import scalaz.syntax.std.option._

import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks

import coinffeine.common.test.UnitTest
import coinffeine.model.order.OrderStatus

class OrderStatusRoundtripSerializationTest extends UnitTest with PropertyChecks {

   val orderStatuses = Gen.oneOf(
     OrderStatus.NotStarted,
     OrderStatus.InProgress,
     OrderStatus.Completed,
     OrderStatus.Cancelled
   )

   "Order status" should "support roundtrip serialization to string" in {
     forAll(orderStatuses) { (original: OrderStatus) =>
       val serialized = OrderStatusFormatter.format(original)
       val deserialized = OrderStatusParser.parse(serialized)
       deserialized shouldBe original.some
     }
   }
 }
