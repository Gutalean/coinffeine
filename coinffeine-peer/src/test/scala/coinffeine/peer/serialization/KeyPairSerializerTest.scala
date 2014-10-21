package coinffeine.peer.serialization

import java.io.ByteArrayOutputStream

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin._

class KeyPairSerializerTest extends UnitTest {

  val kryo = new Kryo()
  kryo.register(classOf[KeyPair], new KeyPairSerializer)

  "Key pairs" should "support roundtrip Kryo serialization" in {
    val keyPair = new KeyPair()
    KeyPairUtils.equals(keyPair, deserialize(serialize(keyPair))) shouldBe true
  }

  "Public keys" should "support roundtrip Kryo serialization" in {
    val publicKey = new KeyPair().publicKey
    KeyPairUtils.equals(publicKey, deserialize(serialize(publicKey))) shouldBe true
  }

  def serialize(keyPair: KeyPair): Array[Byte] = {
    val stream = new ByteArrayOutputStream()
    val output = new Output(stream)
    kryo.writeObject(output, keyPair)
    output.flush()
    stream.toByteArray
  }

  def deserialize(bytes: Array[Byte]): KeyPair = {
    kryo.readObject(new Input(bytes), classOf[KeyPair])
  }
}
