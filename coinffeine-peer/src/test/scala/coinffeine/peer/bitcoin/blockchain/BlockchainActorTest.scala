package coinffeine.peer.bitcoin.blockchain

import scala.concurrent.duration._

import akka.actor.Props
import akka.testkit._
import org.bitcoinj.core.Wallet.SendRequest

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.bitcoin.test.BitcoinjTest
import coinffeine.model.bitcoin._
import coinffeine.model.currency._
import coinffeine.model.exchange.Both
import coinffeine.peer.bitcoin.MockTransactionBroadcaster
import coinffeine.peer.bitcoin.wallet.SmartWallet

class BlockchainActorTest extends AkkaSpec("BlockChainActorTest") with BitcoinjTest {

  "The blockchain actor" must "report transaction confirmation" in new Fixture {
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(tx.getHash, 1))
    expectNoMsg()
    sendToBlockChain(tx)
    requester.expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, 1))
  }

  it must "report multisigned transaction confirmation" in new Fixture {
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(multisignedTx.get.getHash, 1))
    expectNoMsg()
    sendToBlockChain(multisignedTx.get)
    requester.expectMsg(BlockchainActor.TransactionConfirmed(multisignedTx.get.getHash, 1))
  }

  it must "report transaction confirmation when watching for it after the fact" in new Fixture {
    requester.send(instance, BlockchainActor.WatchMultisigKeys(Seq(keyPair, otherKeyPair)))
    expectNoMsg()
    sendToBlockChain(multisignedTx.get)
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(multisignedTx.get.getHash, 1))
    requester.expectMsg(BlockchainActor.TransactionConfirmed(multisignedTx.get.getHash, 1))
  }

  it must "report transaction confirmation only once" in new Fixture {
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(tx.getHash, 2))
    expectNoMsg()
    sendToBlockChain(tx)
    mineBlock()
    requester.expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, 2))
    mineBlock()
    expectNoMsg()
  }

  it must "not report transaction confirmation when still unconfirmed" in new Fixture {
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(tx.getHash, 3))
    expectNoMsg()
    sendToBlockChain(tx)
    expectNoMsg()
  }

  it must "report transaction rejection when it's lost from the blockchain" in new Fixture {
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(tx.getHash, 3))
    expectNoMsg()
    val forkBlock = chainHead()
    sendToBlockChain(tx)
    val forkPlusOneBlock = sendToBlockChain(forkBlock, otherKeyPair)
    sendToBlockChain(forkPlusOneBlock, otherKeyPair)
    requester.expectMsg(BlockchainActor.TransactionRejected(tx.getHash))
  }

  it must "report transaction confirmation after blockchain fork including the tx" in new Fixture {
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(tx.getHash, 2))
    expectNoMsg()
    val forkBlock = chainHead()
    sendToBlockChain(tx)
    val forkPlusOneBlock = sendToBlockChain(forkBlock, otherKeyPair, tx)
    sendToBlockChain(forkPlusOneBlock, otherKeyPair)
    requester.expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, 2))
  }

  it must "report transaction confirmation after an attempt of blockchain fork" in new Fixture {
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(tx.getHash, 2))
    expectNoMsg()
    val forkBlock = chainHead()
    val origPlusOneBlock = sendToBlockChain(tx)
    val forkPlusOneBlock = sendToBlockChain(forkBlock, otherKeyPair, tx)
    sendToBlockChain(origPlusOneBlock, otherKeyPair)
    requester.expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, 2))
  }

  it must "report concurrent transaction confirmations" in new Fixture {
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(tx.getHash, 2))
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(otherTx.getHash, 3))
    expectNoMsg()
    val block = sendToBlockChain(tx, otherTx)
    mineBlock()
    expectBlockHavingConfirmations(block, 2)
    requester.expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, 2))
    mineBlock()
    expectBlockHavingConfirmations(block, 3)
    requester.expectMsg(BlockchainActor.TransactionConfirmed(otherTx.getHash, 3))
  }

  it must "retrieve existing transaction in blockchain" in new Fixture {
    requester.send(instance, BlockchainActor.WatchPublicKey(keyPair))
    expectNoMsg()
    sendToBlockChain(tx)
    requester.send(instance, BlockchainActor.RetrieveTransaction(tx.getHash))
    requester.expectMsg(BlockchainActor.TransactionFound(tx.getHash, ImmutableTransaction(tx)))
  }

  it must "retrieve existing multisigned transaction in blockchain" in new Fixture {
    requester.send(instance, BlockchainActor.WatchMultisigKeys(Seq(keyPair, otherKeyPair)))
    expectNoMsg()
    sendToBlockChain(multisignedTx.get)
    requester.send(instance, BlockchainActor.RetrieveTransaction(multisignedTx.get.getHash))
    requester.expectMsg(BlockchainActor.TransactionFound(multisignedTx.get.getHash, multisignedTx))
  }

  it must "fail to retrieve nonexistent transaction in blockchain" in new Fixture {
    requester.send(instance, BlockchainActor.WatchPublicKey(keyPair))
    requester.send(instance, BlockchainActor.RetrieveTransaction(tx.getHash))
    requester.expectMsg(BlockchainActor.TransactionNotFound(tx.getHash))
  }

  it must "report blockchain height after the blockchain reaches the notification threshold" in
    new Fixture {
      requester.send(instance, BlockchainActor.WatchBlockchainHeight(50))
      expectNoMsg()
      for (currentHeight <- chain.getBestChainHeight to 48) {
        mineBlock()
      }
      expectNoMsg()
      mineBlock()
      requester.expectMsg(BlockchainActor.BlockchainHeightReached(50))
    }

  it must "report blockchain height immediately when asking for an already reached height" in
    new Fixture {
      requester.send(instance, BlockchainActor.WatchBlockchainHeight(chain.getBestChainHeight - 1))
      requester.expectMsg(BlockchainActor.BlockchainHeightReached(chain.getBestChainHeight))
      requester.send(instance, BlockchainActor.WatchBlockchainHeight(chain.getBestChainHeight))
      requester.expectMsg(BlockchainActor.BlockchainHeightReached(chain.getBestChainHeight))
    }

  it must "retrieve the blockchain height" in new Fixture {
    requester.send(instance, BlockchainActor.RetrieveBlockchainHeight)
    requester.expectMsg(BlockchainActor.BlockchainHeightReached(chain.getBestChainHeight))
  }

  it must "notify output is spent" in new Fixture {
    val output = givenAnOutput(0.1.BTC)
    requester.send(instance, BlockchainActor.WatchOutput(output.getOutPointFor))
    expectNoMsg()
    val spendTx = spendOutput(output, 0.05.BTC)
    requester.expectMsg(BlockchainActor.OutputSpent(output.getOutPointFor, spendTx))
  }

  trait Fixture {
    val keyPair = new KeyPair()
    val otherKeyPair = new KeyPair()
    val wallet = new SmartWallet(createWallet(keyPair, 1.BTC))
    val transactionBroadcaster = new MockTransactionBroadcaster()
    val otherWallet = new SmartWallet(createWallet(keyPair, 1.BTC))
    val tx = wallet.delegate.createSend(keyPair.toAddress(network), 0.1.BTC)
    val immutableTx = ImmutableTransaction(tx)
    val otherTx = otherWallet.delegate.createSend(keyPair.toAddress(network), 0.1.BTC)
    val multisignedTx = otherWallet.createMultisignTransaction(Both(keyPair, otherKeyPair), 0.1.BTC)
    val requester = TestProbe()

    val instance = system.actorOf(Props(new BlockchainActor(chain, network)))

    def expectNoMsg(): Unit = {
      requester.expectNoMsg(100.millis.dilated)
    }

    def givenAnOutput(amount: Bitcoin.Amount): MutableTransactionOutput = {
      val txToWatch = wallet.delegate.createSend(wallet.currentReceiveAddress, amount)
      sendToBlockChain(txToWatch)
      txToWatch.getOutput(0)
    }

    def spendOutput(output: MutableTransactionOutput,
                    amount: Bitcoin.Amount): ImmutableTransaction = {
      val spendTx = ImmutableTransaction{
        val tx = new MutableTransaction(network)
        tx.addInput(output)
        tx.addOutput(amount, new KeyPair())
        wallet.delegate.signTransaction(SendRequest.forTx(tx))
        tx
      }
      sendToBlockChain(spendTx.get)
      spendTx
    }
  }
}
