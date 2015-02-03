package coinffeine.peer.exchange.protocol.impl

import org.bitcoinj.core.VerificationException

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.currency._
import coinffeine.peer.exchange.protocol.Handshake.InvalidRefundTransaction

class DefaultHandshakeTest extends ExchangeTest {

  "The refund transaction" should "refund the right amount for the buyer" in new BuyerHandshake {
    valueSent(buyerHandshake.myUnsignedRefund, buyerWallet) should be (1.BTC)
  }

  it should "refund the right amount for the seller" in new SellerHandshake {
    valueSent(sellerHandshake.myUnsignedRefund, sellerWallet) should be (10.BTC)
  }

  it should "not be directly broadcastable to the blockchain" in new BuyerHandshake {
    a [VerificationException] should be thrownBy {
      sendToBlockChain(buyerHandshake.myUnsignedRefund.get)
    }
  }

  it should "not be broadcastable if locktime hasn't expired yet" in new BuyerHandshake {
    sendToBlockChain(buyerHandshake.myDeposit.get)
    a [VerificationException] should be thrownBy {
      sendToBlockChain(buyerHandshake.myUnsignedRefund.get)
    }
  }

  it should "not be broadcastable after locktime when unsigned" in new BuyerHandshake {
    sendToBlockChain(buyerHandshake.myDeposit.get)
    mineUntilLockTime(parameters.lockTime)
    a [VerificationException] should be thrownBy {
      sendToBlockChain(buyerHandshake.myUnsignedRefund.get)
    }
  }

  it should "be broadcastable after locktime if it has been signed" in
    new BuyerHandshake with SellerHandshake {
      val signature = sellerHandshake.signHerRefund(buyerHandshake.myUnsignedRefund)
      val signedBuyerRefund = buyerHandshake.signMyRefund(signature).get
      sendToBlockChain(buyerHandshake.myDeposit.get)
      mineUntilLockTime(parameters.lockTime)
      sendToBlockChain(signedBuyerRefund)
      buyerWallet.estimatedBalance should be (1.BTC)
    }

  "A handshake" should "reject signing invalid counterpart deposits" in
    new BuyerHandshake with SellerHandshake {
      val depositWithWrongLockTime = ImmutableTransaction {
        val tx = buyerHandshake.myUnsignedRefund.get
        tx.clearInputs()
        tx
      }
      an [InvalidRefundTransaction] should be thrownBy {
        sellerHandshake.signHerRefund(depositWithWrongLockTime)
      }
    }
}
