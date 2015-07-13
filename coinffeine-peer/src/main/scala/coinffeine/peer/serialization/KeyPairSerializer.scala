package coinffeine.peer.serialization

import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, Serializer}
import org.bitcoinj.core.ECKey

import coinffeine.model.bitcoin._

class KeyPairSerializer extends Serializer[KeyPair] {

  override def write(kryo: Kryo, output: Output, keyPair: KeyPair): Unit = {
    output.writeBoolean(keyPair.canSign)
    val bytes = if (keyPair.canSign) keyPair.getPrivKeyBytes else keyPair.getPubKey
    output.writeInt(bytes.length)
    output.write(bytes)
  }

  override def read(kryo: Kryo, input: Input, clazz: Class[KeyPair]): KeyPair = {
    val hasPrivKey = input.readBoolean()
    val size = input.readInt()
    val bytes = input.readBytes(size)
    if (hasPrivKey) ECKey.fromPrivate(bytes) else ECKey.fromPublicOnly(bytes)
  }
}
