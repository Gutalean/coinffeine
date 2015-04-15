package coinffeine.peer.exchange.protocol.impl

import scala.collection.JavaConversions._

import org.bitcoinj.core.Wallet.SendRequest
import org.scalatest.Inside

import coinffeine.model.Both
import coinffeine.model.bitcoin.{ImmutableTransaction, MutableTransaction}
import coinffeine.model.currency._

class DepositValidatorTest extends ExchangeTest with Inside {

  "Valid deposits" should "not be built from an invalid buyer commitment transaction" in
    new Fixture {
      val deposits = Both(
        buyer = ImmutableTransaction(invalidFundsCommitment),
        seller = sellerHandshake.myDeposit
      )
      inside(validator.validate(deposits)) {
        case Both(buyerResult, sellerResult) =>
          buyerResult shouldBe 'failure
          sellerResult shouldBe 'success
      }
  }

  it should "not be built from an invalid seller commitment transaction" in new Fixture {
    private val deposits = Both(
      buyer = buyerHandshake.myDeposit,
      seller = ImmutableTransaction(invalidFundsCommitment)
    )
    inside(validator.validate(deposits)) {
      case Both(buyerResult, sellerResult) =>
        buyerResult shouldBe 'success
        sellerResult shouldBe 'failure
    }
  }

  trait Fixture extends BuyerHandshake with SellerHandshake {
    sendMoneyToWallet(sellerWallet.delegate, 10.BTC)
    val invalidFundsCommitment = new MutableTransaction(parameters.network)
    invalidFundsCommitment.addInput(sellerWallet.delegate.calculateAllSpendCandidates(true).head)
    invalidFundsCommitment.addOutput(5.BTC, sellerWallet.freshKeyPair())
    sellerWallet.delegate.signTransaction(SendRequest.forTx(invalidFundsCommitment))
    val validator = new DepositValidator(
      amounts, buyerHandshakingExchange.requiredSignatures, parameters.network)
  }
}
