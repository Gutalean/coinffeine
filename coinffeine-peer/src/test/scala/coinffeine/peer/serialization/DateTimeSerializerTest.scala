package coinffeine.peer.serialization

import org.joda.time.DateTime

class DateTimeSerializerTest extends SerializerTest {

  "Date time" should "support roundtrip Kryo serialization" in new Fixture {
    val instant = new DateTime()
    instant shouldBe serializationRoundtrip(instant)
  }
}
