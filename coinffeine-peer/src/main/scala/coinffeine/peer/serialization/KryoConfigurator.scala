package coinffeine.peer.serialization

import com.esotericsoftware.kryo.Kryo

import coinffeine.model.bitcoin.KeyPair

class KryoConfigurator {
  def customize(kryo: Kryo): Unit  = {
    kryo.register(classOf[KeyPair], new KeyPairSerializer)
    kryo.setReferences(true)
  }
}
