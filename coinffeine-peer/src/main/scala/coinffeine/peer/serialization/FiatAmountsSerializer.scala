package coinffeine.peer.serialization

import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, Serializer}

import coinffeine.model.currency.{FiatAmount, FiatAmounts}

class FiatAmountsSerializer extends Serializer[FiatAmounts] {

  override def write(kryo: Kryo, output: Output, value: FiatAmounts): Unit = {
    kryo.writeObject(output, value.amounts.toVector)
  }

  override def read(kryo: Kryo, input: Input, _class: Class[FiatAmounts]): FiatAmounts = {
    FiatAmounts(kryo.readObject(input, classOf[Vector[FiatAmount]]))
  }
}
