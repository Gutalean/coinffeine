package coinffeine.peer.serialization

import java.io.ByteArrayOutputStream

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin._
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork

class KeyPairSerializerTest extends UnitTest {

  val kryo = new Kryo()
  new KryoConfigurator().customize(kryo)

  "Key pairs" should "support roundtrip Kryo serialization" in {
    val keyPair = new KeyPair()
    KeyPairUtils.equals(keyPair, deserialize(serialize(keyPair))) shouldBe true
  }

  "Public keys" should "support roundtrip Kryo serialization" in {
    val publicKey = new KeyPair().publicKey
    KeyPairUtils.equals(publicKey, deserialize(serialize(publicKey))) shouldBe true
  }

  "Deterministic keys" should "support roundtrip Kryo serialization" in {
    val wallet = new Wallet(CoinffeineUnitTestNetwork)
    val keyPair = wallet.freshReceiveKey()
    KeyPairUtils.equals(keyPair, deserialize(serialize(keyPair))) shouldBe true
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
