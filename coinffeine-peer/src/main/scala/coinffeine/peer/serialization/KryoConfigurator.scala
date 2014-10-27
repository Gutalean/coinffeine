package coinffeine.peer.serialization

import com.esotericsoftware.kryo.Kryo
import org.bitcoinj.crypto.DeterministicKey

import coinffeine.model.bitcoin.KeyPair

class KryoConfigurator {
  def customize(kryo: Kryo): Unit  = {
    val keyPairSerializer = new KeyPairSerializer
    kryo.register(classOf[KeyPair], keyPairSerializer)
    kryo.register(classOf[DeterministicKey], keyPairSerializer)
    kryo.setReferences(true)
  }
}
