package coinffeine.peer.exchange.protocol.impl

import coinffeine.model.bitcoin.TransactionSizeFeeCalculator
import coinffeine.model.currency._
import coinffeine.peer.bitcoin.wallet.SmartWallet

class DefaultExchangeProtocolTest extends ExchangeTest {

  "An exchange protocol" should
    "start a handshake with a deposit of the right amount for the buyer" in new BuyerHandshake {
      val deposit = buyerHandshake.myDeposit.get
      deposit.getValue(buyerWallet.delegate).value shouldBe (-amounts.bitcoinRequired.buyer.units)
      sendToBlockChain(deposit)
    }

  it should "start a handshake with a deposit of the right amount for the seller" in
    new SellerHandshake {
      val deposit = sellerHandshake.myDeposit.get
      deposit.getValue(sellerWallet.delegate).value shouldBe (-amounts.bitcoinRequired.seller.units)
      sendToBlockChain(deposit)
    }

  it should "require the deposit to be valid" in new FreshInstance {
    val buyerWallet = new SmartWallet(createWallet(participants.buyer.bitcoinKey, 1.BTC), TransactionSizeFeeCalculator)
    val invalidDeposit = buyerWallet.createMultisignTransaction(requiredSignatures, 0.1.BTC)
    an [IllegalArgumentException] should be thrownBy {
      protocol.createHandshake(buyerHandshakingExchange, invalidDeposit)
    }
  }
}
