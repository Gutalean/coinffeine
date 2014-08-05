package coinffeine.model.bitcoin

import java.math.BigInteger
import scala.collection.JavaConversions._

import com.google.bitcoin.core.Transaction.SigHash
import com.google.bitcoin.script.ScriptBuilder
import com.google.bitcoin.wallet.WalletTransaction

import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency.Implicits._
import coinffeine.model.currency.{BitcoinAmount, Currency}

object Implicits {

  implicit class PimpMyMutableTransaction(val tx: MutableTransaction) extends AnyVal {

    def addChangeOutput(inputAmount: BitcoinAmount,
                        spentAmount: BitcoinAmount,
                        changeAddress: Address): Unit = {
      val changeAmount = inputAmount - spentAmount
      require(!changeAmount.isNegative)
      if (changeAmount.isPositive) {
        tx.addOutput((inputAmount - spentAmount).asSatoshi, changeAddress)
      }
    }

    def addMultisignOutput(amount: BitcoinAmount, requiredSignatures: Seq[PublicKey]): Unit = {
      require(requiredSignatures.size > 1, "should have at least two signatures")
      tx.addOutput(
        amount.asSatoshi,
        ScriptBuilder.createMultiSigOutputScript(requiredSignatures.size, requiredSignatures)
      )
    }

    def outputAmount: BitcoinAmount = Currency.Bitcoin.fromSatoshi(
      tx.getOutputs.foldLeft(BigInteger.ZERO)((a, b) => a.add(b.getValue)))
  }

  implicit class PimpMyWallet(val wallet: Wallet) extends AnyVal {

    def value(tx: MutableTransaction): BitcoinAmount =
      Currency.Bitcoin.fromSatoshi(tx.getValue(wallet))

    def valueSentFromMe(tx: MutableTransaction): BitcoinAmount =
      Currency.Bitcoin.fromSatoshi(tx.getValueSentFromMe(wallet))

    def valueSentToMe(tx: MutableTransaction): BitcoinAmount =
      Currency.Bitcoin.fromSatoshi(tx.getValueSentToMe(wallet))

    def balance(): BitcoinAmount = Currency.Bitcoin.fromSatoshi(wallet.getBalance)

    def blockFunds(tx: MutableTransaction): Unit = {
      wallet.commitTx(tx)
    }

    def blockFunds(to: Address, amount: BitcoinAmount): MutableTransaction = {
      val tx = wallet.createSend(to, amount.asSatoshi)
      blockFunds(tx)
      tx
    }

    def blockMultisignFunds(requiredSignatures: Seq[PublicKey],
                            amount: BitcoinAmount): MutableTransaction = {
      require(amount.isPositive, s"Amount to block must be greater than zero ($amount given)")

      val inputFunds = collectFunds(amount)
      val totalInputFunds = valueOf(inputFunds)
      require(totalInputFunds >= amount,
        "Input funds must cover the amount of funds to commit")

      val tx = new MutableTransaction(wallet.getNetworkParameters)
      inputFunds.foreach(tx.addInput)
      tx.addMultisignOutput(amount, requiredSignatures)
      tx.addChangeOutput(totalInputFunds, amount, wallet.getChangeAddress)
      tx.signInputs(SigHash.ALL, wallet)
      blockFunds(tx)
      tx
    }

    def releaseFunds(tx: MutableTransaction): Unit = {
      val walletTx = getTransaction(tx.getHash).getOrElse(
        throw new IllegalArgumentException(s"${tx.getHashAsString} is not part of this wallet"))
      walletTx.getInputs.foreach { input =>
        val parentTx = input.getOutpoint.getConnectedOutput.getParentTransaction
        if (contains(parentTx)) {
          if (!input.disconnect()) {
            throw new IllegalStateException(s"cannot disconnect outputs from $input in $walletTx")
          }
          moveToPool(parentTx, WalletTransaction.Pool.UNSPENT)
        }
      }
      moveToPool(walletTx, WalletTransaction.Pool.DEAD)
    }

    def collectFunds(amount: BitcoinAmount): Set[MutableTransactionOutput] = {
      val inputFundCandidates = wallet.calculateAllSpendCandidates(true)
      val necessaryInputCount =
        inputFundCandidates.view.scanLeft(Currency.Bitcoin.Zero)((accum, output) =>
          accum + Currency.Bitcoin.fromSatoshi(output.getValue))
            .takeWhile(_ < amount)
            .length
      inputFundCandidates.take(necessaryInputCount).toSet
    }

    def contains(tx: MutableTransaction): Boolean = getTransaction(tx.getHash).isDefined

    private def getTransaction(txHash: Hash) = Option(wallet.getTransaction(txHash))

    private def valueOf(outputs: Traversable[MutableTransactionOutput]): BitcoinAmount =
      outputs.map(funds => Currency.Bitcoin.fromSatoshi(funds.getValue))
        .foldLeft(Bitcoin.Zero)(_ + _)

    private def moveToPool(tx: MutableTransaction, pool: WalletTransaction.Pool): Unit = {
      val wtxs = wallet.getWalletTransactions
      wallet.clearTransactions(0)
      wallet.addWalletTransaction(new WalletTransaction(pool, tx))
      wtxs.foreach { wtx =>
        if (tx.getHash != wtx.getTransaction.getHash) {
          wallet.addWalletTransaction(wtx)
        }
      }
    }
  }

  implicit class PimpMyKeyPair(val keyPair: KeyPair) extends AnyVal {

    /** Copies just the public key */
    def publicKey: PublicKey = new PublicKey(null, keyPair.getPubKey)
  }
}
