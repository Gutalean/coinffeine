package coinffeine.model.bitcoin

import scala.collection.JavaConversions._
import scala.language.implicitConversions

import org.bitcoinj.core.Coin
import org.bitcoinj.core.ECKey.MissingPrivateKeyException
import org.bitcoinj.script.ScriptBuilder

import coinffeine.model.currency._

trait Implicits {
  import Implicits._

  implicit def pimpMyMutableTransaction(tx: MutableTransaction) = new PimpMyMutableTransaction(tx)
  implicit def pimpMyKeyPair(keyPair: KeyPair) = new PimpMyKeyPair(keyPair)
}

object Implicits {
  class PimpMyMutableTransaction(val tx: MutableTransaction) extends AnyVal {

    def addChangeOutput(inputAmount: Bitcoin.Amount,
                        spentAmount: Bitcoin.Amount,
                        changeAddress: Address): Unit = {
      val changeAmount = inputAmount - spentAmount
      require(!changeAmount.isNegative)
      if (changeAmount.isPositive) {
        tx.addOutput(inputAmount - spentAmount, changeAddress)
      }
    }

    def addMultisignOutput(amount: Bitcoin.Amount, requiredSignatures: Seq[PublicKey]): Unit = {
      require(requiredSignatures.size > 1, "should have at least two signatures")
      tx.addOutput(
        amount,
        ScriptBuilder.createMultiSigOutputScript(requiredSignatures.size, requiredSignatures)
      )
    }

    def outputAmount: Bitcoin.Amount = tx.getOutputs.foldLeft(Coin.ZERO)((a, b) => a.add(b.getValue))
  }

  class PimpMyKeyPair(val keyPair: KeyPair) extends AnyVal {

    /** Copies just the public key */
    def publicKey: PublicKey = PublicKey(keyPair.getPubKey)

    def canSign: Boolean = try {
      keyPair.getPrivKey != null
    } catch {
      case _: MissingPrivateKeyException => false
    }
  }
}
