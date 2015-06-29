package coinffeine.peer.serialization

import java.io.ByteArrayOutputStream
import scala.reflect.ClassTag

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}

import coinffeine.common.test.UnitTest

/** Base trait for testing custom Kryo serializers */
trait SerializerTest extends UnitTest {

  protected trait Fixture {
    protected val kryo = {
      val k = new Kryo()
      new KryoConfigurator().customize(k)
      k
    }

    protected def serializationRoundtrip[T](value: T)(implicit ev: ClassTag[T]): T = {
      serializationRoundtrip(value, ev.runtimeClass.asInstanceOf[Class[T]])
    }

    protected def serializationRoundtrip[T](value: T, clazz: Class[T]): T =
      deserialize(serialize(value), clazz)

    protected def serialize[T](keyPair: T): Array[Byte] = {
      val stream = new ByteArrayOutputStream()
      val output = new Output(stream)
      kryo.writeObject(output, keyPair)
      output.flush()
      stream.toByteArray
    }

    protected def deserialize[T](bytes: Array[Byte], clazz: Class[T]): T = {
      kryo.readObject(new Input(bytes), clazz)
    }
  }
}
