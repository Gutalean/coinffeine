package coinffeine.peer.serialization

import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, Serializer}
import org.joda.time.DateTime

class DateTimeSerializer extends Serializer[DateTime] {

  override def write(kryo: Kryo, output: Output, timestamp: DateTime): Unit = {
    output.writeLong(timestamp.getMillis)
  }

  override def read(kryo: Kryo, input: Input, clazz: Class[DateTime]): DateTime =
    new DateTime(input.readLong())
}
