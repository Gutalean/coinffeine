package coinffeine.peer.exchange.protocol.impl

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.bitcoin.Implicits._
import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency.Implicits._

class DefaultExchangeProtocolTest extends ExchangeTest {

  "An exchange protocol" should
    "start a handshake with a deposit of the right amount for the buyer" in new BuyerHandshake {
      val deposit = buyerHandshake.myDeposit.get
      Bitcoin.fromSatoshi(deposit.getValue(buyerWallet)) should be (-2.BTC)
      sendToBlockChain(deposit)
    }

  it should "start a handshake with a deposit of the right amount for the seller" in
    new SellerHandshake {
      val deposit = sellerHandshake.myDeposit.get
      Bitcoin.fromSatoshi(deposit.getValue(sellerWallet)) should be (-11.BTC)
      sendToBlockChain(deposit)
    }

  it should "require the deposit to be valid" in new FreshInstance {
    val buyerWallet = createWallet(participants.buyer.bitcoinKey, 1.BTC)
    val invalidDeposit = ImmutableTransaction(
      buyerWallet.blockMultisignFunds(requiredSignatures, 0.1.BTC))
    an [IllegalArgumentException] should be thrownBy {
      protocol.createHandshake(buyerHandshakingExchange, invalidDeposit)
    }
  }
}
