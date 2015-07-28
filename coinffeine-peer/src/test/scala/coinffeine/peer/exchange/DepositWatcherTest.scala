package coinffeine.peer.exchange

import scala.collection.JavaConverters._
import scala.concurrent.duration._

import akka.actor.Props
import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.bitcoin._
import coinffeine.model.bitcoin.test.BitcoinjTest
import coinffeine.model.currency._
import coinffeine.model.exchange.SampleExchange
import coinffeine.peer.bitcoin.blockchain.BlockchainActor
import coinffeine.peer.bitcoin.wallet.SmartWallet
import coinffeine.peer.exchange.DepositWatcher._
import coinffeine.peer.exchange.protocol.impl.TransactionProcessor

class DepositWatcherTest extends AkkaSpec with BitcoinjTest with SampleExchange {

  "A deposit watcher" should "notify my deposit refund" in new NoCounterpartDepositFixture {
    blockchain.expectMsg(BlockchainActor.WatchOutput(myOutput))
    expectNoMsg(100.millis)
    blockchain.reply(BlockchainActor.OutputSpent(myOutput, myRefund))
    expectMsg(DepositWatcher.DepositSpent(myRefund, DepositRefund))
  }

  it should "notify counterpart deposit refund" in new CounterpartDepositFixture {
    blockchain.expectMsg(BlockchainActor.WatchOutput(myOutput))
    blockchain.expectMsg(BlockchainActor.WatchOutput(herOutput))
    expectNoMsg(100.millis)
    blockchain.reply(BlockchainActor.OutputSpent(herOutput, herRefund))
    expectMsg(DepositWatcher.DepositSpent(herRefund, CounterpartDepositRefund))
  }

  it should "notify successful channel publication" in new NoCounterpartDepositFixture {
    val happyPathTx = spendDeposit(exchange.amounts.finalStep.depositSplit.seller, myDeposit)
    givenOutputIsSpentWith(happyPathTx)
    expectMsg(DepositWatcher.DepositSpent(happyPathTx, CompletedChannel))
  }

  it should "notify channel publication during an intermediate step" in new NoCounterpartDepositFixture {
    val thirdStepChannelTx = spendDeposit(exchange.amounts.intermediateSteps(2).depositSplit.seller, myDeposit)
    givenOutputIsSpentWith(thirdStepChannelTx)
    expectMsg(DepositWatcher.DepositSpent(thirdStepChannelTx, ChannelAtStep(3)))
  }

  it should "notify unexpected deposit destinations" in new NoCounterpartDepositFixture {
    private val unexpectedAmount = 0.31416.BTC
    val interruptedChannelTx = spendDeposit(unexpectedAmount, myDeposit)
    givenOutputIsSpentWith(interruptedChannelTx)
    expectMsg(DepositWatcher.DepositSpent(interruptedChannelTx, UnexpectedDestination))
  }

  trait Fixture {
    private val myWallet = new SmartWallet(createWallet(100.BTC), TransactionSizeFeeCalculator)
    requiredSignatures.foreach(myWallet.delegate.importKey)
    val myDeposit = myWallet.createMultisignTransaction(
      requiredSignatures,
      100.BTC
    )
    val myOutput = myDeposit.get.getOutput(0)
    sendToBlockChain(myDeposit.get)
    val exchange = sellerHandshakingExchange
    val myRefund = spendDeposit(exchange.amounts.refunds.seller, myDeposit)
    val blockchain = TestProbe()

    def counterpartDeposit: Option[ImmutableTransaction]

    val watcher = system.actorOf(Props(
      new DepositWatcher(exchange, myDeposit, myRefund, counterpartDeposit, Collaborators(blockchain.ref, listener = self))))

    def spendDeposit(getBackAmount: BitcoinAmount, deposit: ImmutableTransaction) = {
      val tx = TransactionProcessor.createUnsignedTransaction(
        inputs = deposit.get.getOutputs.asScala,
        outputs = Seq(participants.seller.bitcoinKey -> getBackAmount),
        network = network
      )
      tx.getInput(0).setSignatures(
        tx.signMultisigOutput(0, participants.buyer.bitcoinKey, exchange.requiredSignatures.toSeq),
        tx.signMultisigOutput(0, participants.seller.bitcoinKey, exchange.requiredSignatures.toSeq)
      )
      ImmutableTransaction(tx)
    }

    def givenOutputIsSpentWith(spendTx: ImmutableTransaction): Unit = {
      blockchain.expectMsg(BlockchainActor.WatchOutput(myOutput))
      blockchain.reply(BlockchainActor.OutputSpent(myOutput, spendTx))
    }
  }

  trait NoCounterpartDepositFixture extends Fixture {
    override lazy val counterpartDeposit = None
  }

  trait CounterpartDepositFixture extends Fixture {
    private lazy val herWallet = new SmartWallet(createWallet(100.BTC), TransactionSizeFeeCalculator)
    requiredSignatures.foreach(herWallet.delegate.importKey)
    lazy val herDeposit = herWallet.createMultisignTransaction(
      requiredSignatures,
      100.BTC
    )
    val herOutput = herDeposit.get.getOutput(0)
    sendToBlockChain(herDeposit.get)
    val herRefund = spendDeposit(exchange.amounts.refunds.buyer, herDeposit)

    override lazy val counterpartDeposit = Some(herDeposit)
  }
}
