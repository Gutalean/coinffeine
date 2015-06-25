package coinffeine.model.bitcoin

import scala.collection.JavaConversions._
import scala.language.implicitConversions

import org.bitcoinj.core.ECKey.MissingPrivateKeyException
import org.bitcoinj.core.Transaction.SigHash
import org.bitcoinj.core.{Coin, TransactionInput}
import org.bitcoinj.script.ScriptBuilder

import coinffeine.model.currency._

trait Implicits {
  import Implicits._

  implicit def pimpMyMutableTransaction(tx: MutableTransaction): PimpMyMutableTransaction =
    new PimpMyMutableTransaction(tx)
  implicit def pimpMyKeyPair(keyPair: KeyPair): PimpMyKeyPair = new PimpMyKeyPair(keyPair)
  implicit def pimpMyInput(input: TransactionInput): PimpMyInput = new PimpMyInput(input)
}

object Implicits {
  class PimpMyMutableTransaction(val tx: MutableTransaction) extends AnyVal {

    def addChangeOutput(inputAmount: BitcoinAmount,
                        spentAmount: BitcoinAmount,
                        changeAddress: Address): Unit = {
      val changeAmount = inputAmount - spentAmount
      require(!changeAmount.isNegative)
      if (changeAmount.isPositive) {
        tx.addOutput(inputAmount - spentAmount, changeAddress)
      }
    }

    def addMultisigOutput(amount: BitcoinAmount, requiredSignatures: Seq[PublicKey]): Unit = {
      require(requiredSignatures.size > 1, "should have at least two signatures")
      tx.addOutput(
        amount,
        ScriptBuilder.createMultiSigOutputScript(requiredSignatures.size, requiredSignatures)
      )
    }

    def signMultisigOutput(index: Int,
                           signAs: KeyPair,
                           requiredSignatures: Seq[PublicKey]): TransactionSignature = {
      val script = ScriptBuilder.createMultiSigOutputScript(
        requiredSignatures.size, requiredSignatures)
      tx.calculateSignature(index, signAs, script, SigHash.ALL, false)
    }

    def outputAmount: BitcoinAmount = tx.getOutputs.foldLeft(Coin.ZERO)((a, b) => a.add(b.getValue))
  }

  class PimpMyInput(val input: TransactionInput) extends AnyVal {
    def setSignatures(signatures: TransactionSignature*): Unit = {
      input.setScriptSig(ScriptBuilder.createMultiSigInputScript(signatures))
    }
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
