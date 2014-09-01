package coinffeine.peer.exchange.protocol.impl

import scala.collection.JavaConversions._

import com.google.bitcoin.core.Transaction.SigHash

import coinffeine.model.bitcoin.{ImmutableTransaction, MutableTransaction}
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange.Both

class DepositValidatorTest extends ExchangeTest {

  "Valid deposits" should "not be built from an invalid buyer commitment transaction" in
    new Fixture {
      validator.validate(Both(
        buyer = ImmutableTransaction(invalidFundsCommitment),
        seller = sellerHandshake.myDeposit
      )) should be ('failure)
  }

  it should "not be built from an invalid seller commitment transaction" in new Fixture {
    validator.validate(Both(
      buyer = buyerHandshake.myDeposit,
      seller = ImmutableTransaction(invalidFundsCommitment)
    )) should be ('failure)
  }

  trait Fixture extends BuyerHandshake with SellerHandshake {
    sendMoneyToWallet(sellerWallet, 10.BTC)
    val invalidFundsCommitment = new MutableTransaction(parameters.network)
    invalidFundsCommitment.addInput(sellerWallet.calculateAllSpendCandidates(true).head)
    invalidFundsCommitment.addOutput(5.BTC.asSatoshi, sellerWallet.getKeys.head)
    invalidFundsCommitment.signInputs(SigHash.ALL, sellerWallet)
    val validator = new DepositValidator(amounts, buyerHandshakingExchange.requiredSignatures)
  }
}
