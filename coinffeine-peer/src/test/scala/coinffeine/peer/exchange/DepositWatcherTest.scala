package coinffeine.peer.exchange

import scala.collection.JavaConverters._
import scala.concurrent.duration._

import akka.actor.Props
import akka.testkit.TestProbe
import org.bitcoinj.core.TransactionOutPoint

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.bitcoin.test.BitcoinjTest
import coinffeine.model.bitcoin.{ImmutableTransaction, KeyPair}
import coinffeine.model.currency._
import coinffeine.model.exchange.SampleExchange
import coinffeine.peer.bitcoin.SmartWallet
import coinffeine.peer.bitcoin.blockchain.BlockchainActor
import coinffeine.peer.exchange.DepositWatcher._
import coinffeine.peer.exchange.protocol.impl.TransactionProcessor

class DepositWatcherTest extends AkkaSpec with BitcoinjTest with SampleExchange {

  "A deposit watcher" should "notify deposit refund" in new Fixture {
    blockchain.expectMsg(BlockchainActor.WatchOutput(output))
    expectNoMsg(100.millis)
    blockchain.reply(BlockchainActor.OutputSpent(output, myRefund))
    expectMsg(DepositWatcher.DepositSpent(myRefund, DepositRefund))
  }

  it should "notify successful channel publication" in new Fixture {
    val happyPathTx = spendDeposit(exchange.amounts.finalStep.depositSplit.seller)
    givenOutputIsSpentWith(happyPathTx)
    expectMsg(DepositWatcher.DepositSpent(happyPathTx, CompletedChannel))
  }

  it should "notify channel publication during an intermediate step" in new Fixture {
    val thirdStepChannelTx = spendDeposit(exchange.amounts.intermediateSteps(2).depositSplit.seller)
    givenOutputIsSpentWith(thirdStepChannelTx)
    expectMsg(DepositWatcher.DepositSpent(thirdStepChannelTx, ChannelAtStep(3)))
  }

  it should "notify unexpected deposit destinations" in new Fixture {
    private val unexpectedAmount = 0.31416.BTC
    val interruptedChannelTx = spendDeposit(unexpectedAmount)
    givenOutputIsSpentWith(interruptedChannelTx)
    expectMsg(DepositWatcher.DepositSpent(interruptedChannelTx, UnexpectedDestination))
  }

  trait Fixture {
    private val wallet = new SmartWallet(createWallet(new KeyPair(), 100.BTC))
    requiredSignatures.foreach(wallet.delegate.importKey)
    wallet.blockFunds(exchangeId, 100.BTC)
    val myDeposit = wallet.createMultisignTransaction(
      coinsId = exchangeId,
      amount = 100.BTC,
      fee = 0.BTC,
      signatures = requiredSignatures
    )
    val output = new TransactionOutPoint(network, 0, myDeposit.get.getHash)
    sendToBlockChain(myDeposit.get)
    val exchange = sellerHandshakingExchange
    val myRefund = spendDeposit(exchange.amounts.refunds.seller)
    val blockchain = TestProbe()
    val watcher = system.actorOf(Props(
      new DepositWatcher(exchange, myDeposit, myRefund, Collaborators(blockchain.ref, listener = self))))

    def spendDeposit(getBackAmount: Bitcoin.Amount) = {
      val tx = TransactionProcessor.createUnsignedTransaction(
        inputs = myDeposit.get.getOutputs.asScala,
        outputs = Seq(participants.seller.bitcoinKey -> getBackAmount),
        network = network
      )
      val signatures = Seq(
        TransactionProcessor.signMultiSignedOutput(
          tx, 0, participants.buyer.bitcoinKey, exchange.requiredSignatures.toSeq),
        TransactionProcessor.signMultiSignedOutput(
          tx, 0, participants.seller.bitcoinKey, exchange.requiredSignatures.toSeq)
      )
      TransactionProcessor.setMultipleSignatures(tx, 0, signatures: _*)
      ImmutableTransaction(tx)
    }

    def givenOutputIsSpentWith(spendTx: ImmutableTransaction): Unit = {
      blockchain.expectMsg(BlockchainActor.WatchOutput(output))
      blockchain.reply(BlockchainActor.OutputSpent(output, spendTx))
    }
  }
}
