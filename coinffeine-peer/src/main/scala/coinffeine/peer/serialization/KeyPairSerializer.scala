package coinffeine.peer.serialization

import com.esotericsoftware.kryo.io.{Output, Input}
import com.esotericsoftware.kryo.{Kryo, Serializer}
import org.bitcoinj.core.ECKey

import coinffeine.model.bitcoin.KeyPair

class KeyPairSerializer extends Serializer[KeyPair] {

  override def write(kryo: Kryo, output: Output, keyPair: KeyPair): Unit = {
    output.writeBoolean(keyPair.hasPrivKey)
    val bytes = if (keyPair.hasPrivKey) keyPair.toASN1 else keyPair.getPubKey
    output.writeInt(bytes.length)
    output.write(bytes)
  }

  override def read(kryo: Kryo, input: Input, clazz: Class[KeyPair]): KeyPair = {
    val hasPrivKey = input.readBoolean()
    val size = input.readInt()
    val bytes = input.readBytes(size)
    if (hasPrivKey) ECKey.fromASN1(bytes) else ECKey.fromPublicOnly(bytes)
  }
}
