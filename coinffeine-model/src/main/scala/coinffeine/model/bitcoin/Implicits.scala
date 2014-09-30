package coinffeine.model.bitcoin

import java.math.BigInteger
import scala.collection.JavaConversions._

import com.google.bitcoin.script.ScriptBuilder

import coinffeine.model.currency.Bitcoin
import coinffeine.model.currency.Implicits._

object Implicits {

  implicit class PimpMyMutableTransaction(val tx: MutableTransaction) extends AnyVal {

    def addChangeOutput(inputAmount: Bitcoin.Amount,
                        spentAmount: Bitcoin.Amount,
                        changeAddress: Address): Unit = {
      val changeAmount = inputAmount - spentAmount
      require(!changeAmount.isNegative)
      if (changeAmount.isPositive) {
        tx.addOutput((inputAmount - spentAmount).asSatoshi, changeAddress)
      }
    }

    def addMultisignOutput(amount: Bitcoin.Amount, requiredSignatures: Seq[PublicKey]): Unit = {
      require(requiredSignatures.size > 1, "should have at least two signatures")
      tx.addOutput(
        amount.asSatoshi,
        ScriptBuilder.createMultiSigOutputScript(requiredSignatures.size, requiredSignatures)
      )
    }

    def outputAmount: Bitcoin.Amount = Bitcoin.fromSatoshi(
      tx.getOutputs.foldLeft(BigInteger.ZERO)((a, b) => a.add(b.getValue)))
  }

  implicit class PimpMyKeyPair(val keyPair: KeyPair) extends AnyVal {

    /** Copies just the public key */
    def publicKey: PublicKey = new PublicKey(null, keyPair.getPubKey)
  }
}
