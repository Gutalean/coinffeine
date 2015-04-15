package coinffeine.peer.exchange.protocol.impl

import scala.util.Try
import scala.util.control.NonFatal

import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.RunningExchange
import coinffeine.peer.exchange.protocol.MicroPaymentChannel._
import coinffeine.peer.exchange.protocol._
import coinffeine.peer.exchange.protocol.impl.DefaultMicroPaymentChannel._

private[impl] class DefaultMicroPaymentChannel[C <: FiatCurrency] private (
    override val exchange: RunningExchange[C], override val currentStep: Step)
  extends MicroPaymentChannel[C] {

  def this(exchange: RunningExchange[C]) =
    this(exchange, IntermediateStep(1, exchange.amounts.breakdown))

  private val currentUnsignedTransaction = ImmutableTransaction {
    val split = currentStep.select(exchange.amounts).depositSplit
    val depositOutputs = exchange.deposits.toSeq.map(_.get.getOutput(0))
    TransactionProcessor.createUnsignedTransaction(
      inputs = depositOutputs,
      outputs = Seq(
        exchange.participants.buyer.bitcoinKey -> split.buyer,
        exchange.participants.seller.bitcoinKey -> split.seller
      ),
      network = exchange.parameters.network
    )
  }

  override def validateCurrentTransactionSignatures(
      herSignatures: Both[TransactionSignature]): Try[Unit] = {
    val tx = currentUnsignedTransaction.get
    val herKey = exchange.counterpart.bitcoinKey

    def requireValidSignature(index: Int, signature: TransactionSignature) = {
      require(
        TransactionProcessor.isValidSignature(tx, index, signature, herKey,
          exchange.requiredSignatures.toSeq),
        s"Signature $signature cannot satisfy ${tx.getInput(index)}"
      )
    }

    Try {
      requireValidSignature(BuyerDepositInputIndex, herSignatures.buyer)
      requireValidSignature(SellerDepositInputIndex, herSignatures.seller)
    } recover {
      case NonFatal(cause) => throw InvalidSignaturesException(herSignatures, cause)
    }
  }

  override def signCurrentTransaction = {
    val tx = currentUnsignedTransaction.get
    val signingKey = exchange.user.bitcoinKey
    Both(
      buyer = tx.signMultisigOutput(
        BuyerDepositInputIndex, signingKey, exchange.requiredSignatures.toSeq),
      seller = tx.signMultisigOutput(
        SellerDepositInputIndex, signingKey, exchange.requiredSignatures.toSeq)
    )
  }

  override def nextStep = new DefaultMicroPaymentChannel(exchange, currentStep.next)

  override def closingTransaction(herSignatures: Both[TransactionSignature]) = {
    validateCurrentTransactionSignatures(herSignatures).get
    val tx = currentUnsignedTransaction.get
    val signatures = Seq(signCurrentTransaction, herSignatures)
    tx.getInput(BuyerDepositInputIndex).setSignatures(signatures.map(_.buyer): _*)
    tx.getInput(SellerDepositInputIndex).setSignatures(signatures.map(_.seller): _*)
    ImmutableTransaction(tx)
  }
}

private[impl] object DefaultMicroPaymentChannel {
  val BuyerDepositInputIndex = 0
  val SellerDepositInputIndex = 1
}
