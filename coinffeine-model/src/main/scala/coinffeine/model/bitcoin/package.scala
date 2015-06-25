package coinffeine.model

import java.io.InputStream

import org.bitcoinj.{core, crypto}

import coinffeine.model.currency.{Bitcoin, BitcoinAmount}

package object bitcoin extends Implicits {

  /** Bitcoinj's ECKey contains a public key and optionally the private one. We might replace
    * this typedef by a class ensuring that both public and private key are present.
    */
  type KeyPair = core.ECKey
  type PublicKey = core.ECKey
  object PublicKey {
    def apply(publicKeyBytes: Array[Byte]): PublicKey = core.ECKey.fromPublicOnly(publicKeyBytes)
    def areEqual(left: PublicKey, right: PublicKey): Boolean =
      left.getPubKey.sameElements(right.getPubKey)
  }

  type MutableTransactionOutput = core.TransactionOutput
  type MutableTransaction = core.Transaction
  object MutableTransaction {
    val ReferenceDefaultMinTxFee: BitcoinAmount =
      Bitcoin.fromSatoshi(core.Transaction.REFERENCE_DEFAULT_MIN_TX_FEE.value)
    val MinimumNonDustAmount: BitcoinAmount =
      Bitcoin.fromSatoshi(core.Transaction.MIN_NONDUST_OUTPUT.value)
  }

  type Address = core.Address
  type Hash = core.Sha256Hash
  type Network = core.NetworkParameters

  type TransactionSignature = crypto.TransactionSignature
  object TransactionSignature {

    def dummy = crypto.TransactionSignature.dummy()

    def decode(bytes: Array[Byte], requireCanonical: Boolean = true): TransactionSignature =
      crypto.TransactionSignature.decodeFromBitcoin(bytes, requireCanonical)
  }

  type Wallet = core.Wallet
  object Wallet {

    def defaultFeePerKb: BitcoinAmount = core.Wallet.SendRequest.DEFAULT_FEE_PER_KB

    def defaultFeePerKb_= (value: BitcoinAmount): Unit = {
      core.Wallet.SendRequest.DEFAULT_FEE_PER_KB = value
    }

    def loadFromFileStream(stream: InputStream): Wallet = core.Wallet.loadFromFileStream(stream)
  }
}
