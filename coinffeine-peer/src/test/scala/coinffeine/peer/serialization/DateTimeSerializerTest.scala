package coinffeine.peer.serialization

import org.joda.time.DateTime

class DateTimeSerializerTest extends SerializerTest {

  "Date time" should "support roundtrip Kryo serialization" in {
    val instant = new DateTime()
    instant shouldBe dateTimeSerializationRoundtrip(instant)
  }
}
