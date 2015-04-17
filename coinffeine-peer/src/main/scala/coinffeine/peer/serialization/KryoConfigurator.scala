package coinffeine.peer.serialization

import com.esotericsoftware.kryo.Kryo
import org.bitcoinj.crypto.DeterministicKey

import coinffeine.model.bitcoin.KeyPair
import org.joda.time.DateTime

class KryoConfigurator {
  def customize(kryo: Kryo): Unit  = {
    val keyPairSerializer = new KeyPairSerializer
    kryo.register(classOf[KeyPair], keyPairSerializer)
    kryo.register(classOf[DeterministicKey], keyPairSerializer)
    kryo.register(classOf[DateTime], new DateTimeSerializer)
    kryo.setReferences(true)
  }
}
