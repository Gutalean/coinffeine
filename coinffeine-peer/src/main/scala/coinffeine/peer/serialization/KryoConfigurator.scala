package coinffeine.peer.serialization

import com.esotericsoftware.kryo.Kryo
import org.bitcoinj.crypto.DeterministicKey
import org.joda.time.DateTime

import coinffeine.model.bitcoin.KeyPair
import coinffeine.model.currency.FiatAmounts

class KryoConfigurator {
  def customize(kryo: Kryo): Unit  = {
    overrideFaultySerializationOfBitcoinjKeys(kryo)
    overrideFaultySerializationOfDateTime(kryo)
    overrideFaultySerializationOfFiatAmounts(kryo)
    kryo.setReferences(true)
  }

  private def overrideFaultySerializationOfBitcoinjKeys(kryo: Kryo): Unit = {
    val keyPairSerializer = new KeyPairSerializer
    kryo.register(classOf[KeyPair], keyPairSerializer)
    kryo.register(classOf[DeterministicKey], keyPairSerializer)
  }

  private def overrideFaultySerializationOfDateTime(kryo: Kryo): Unit = {
    kryo.register(classOf[DateTime], new DateTimeSerializer)
  }

  private def overrideFaultySerializationOfFiatAmounts(kryo: Kryo): Unit = {
    kryo.register(classOf[FiatAmounts], new FiatAmountsSerializer)
  }
}
