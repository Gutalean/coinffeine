package coinffeine.peer.exchange.protocol.impl

import coinffeine.model.currency._
import coinffeine.model.exchange.SampleExchange

class DefaultMicroPaymentChannelTest extends ExchangeTest {

  "A micropayment channel" should "generate valid signatures for each step" in new Channels {
    for ((buyer, seller) <- buyerChannels.zip(sellerChannels)) {
      buyer.currentStep should be (seller.currentStep)
      withClue(buyer.currentStep) {
        buyer.validateCurrentTransactionSignatures(seller.signCurrentTransaction) should be ('success)
      }
    }
  }

  for (step <- 1 to SampleExchange.IntermediateSteps) {
    it should s"split the exchanged amount and destroy deposits as fees in the step $step" in
      new Channels {
        val currentBuyerChannel = buyerChannels(step - 1)
        val currentSellerChannel = sellerChannels(step - 1)
        val tx = currentBuyerChannel.closingTransaction(currentSellerChannel.signCurrentTransaction)
        sendToBlockChain(tx.get)
        (buyerWallet.estimatedBalance + sellerWallet.estimatedBalance) should be (10.002.BTC)
        buyerWallet.estimatedBalance should be (1.BTC * step + 0.002.BTC)
      }
  }

  it should "send exchanged amount to the buyer and deposits to depositors in the last step" in
    new Channels {
      val lastBuyerChannel = buyerChannels.last
      val lastSellerChannel = sellerChannels.last
      val tx = lastBuyerChannel.closingTransaction(lastSellerChannel.signCurrentTransaction)
      sendToBlockChain(tx.get)
      buyerWallet.estimatedBalance should be (12.002.BTC)
      sellerWallet.estimatedBalance should be (1.BTC)
    }
}
