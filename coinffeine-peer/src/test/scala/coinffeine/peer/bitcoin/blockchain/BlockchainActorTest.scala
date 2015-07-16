package coinffeine.peer.bitcoin.blockchain

import scala.collection.JavaConverters._
import scala.concurrent.duration._

import akka.actor.Props
import akka.testkit._
import org.bitcoinj.core.Wallet.SendRequest

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.bitcoin.test.BitcoinjTest
import coinffeine.model.currency._
import coinffeine.peer.bitcoin.wallet.SmartWallet

class BlockchainActorTest extends AkkaSpec("BlockChainActorTest") with BitcoinjTest {

  "The blockchain actor" must "report transaction confirmation" in new Fixture {
    givenConfirmationSubscription(tx, 1)
    sendToBlockChain(tx)
    expectConfirmation(tx, 1)
  }

  it must "report multisigned transaction confirmation" in new Fixture {
    val (multisigTx, _) = createMultisigTransaction(0.1.BTC)
    givenConfirmationSubscription(multisigTx.get, 1)
    sendToBlockChain(multisigTx.get)
    expectConfirmation(multisigTx.get, 1)
  }

  it must "report transaction confirmation when watching for it after the fact" in new Fixture {
    expectNoMsg()
    sendToBlockChain(tx)
    expectAlreadyConfirmed(tx, 1)
  }

  it must "report transaction confirmation only once" in new Fixture {
    givenConfirmationSubscription(tx, 2)
    sendToBlockChain(tx)
    mineBlock()
    expectConfirmation(tx, 2)
    mineBlock()
    expectNoMsg()
  }

  it must "not report transaction confirmation when still unconfirmed" in new Fixture {
    givenConfirmationSubscription(tx, 3)
    sendToBlockChain(tx)
    expectNoMsg()
  }

  it must "report transaction confirmation after blockchain fork including the tx" in new Fixture {
    givenConfirmationSubscription(tx, 2)
    val alternativeBranch = new Branch
    sendToBlockChain(tx)
    alternativeBranch.mineBlock(tx)
    alternativeBranch.mineBlock()
    expectConfirmation(tx, 2)
  }

  it must "report transaction confirmation after an attempt of blockchain fork" in new Fixture {
    givenConfirmationSubscription(tx, 2)
    val mainBranch, alternativeBranch = new Branch
    alternativeBranch.mineBlock(tx)
    mainBranch.mineBlock(tx)
    mainBranch.mineBlock()
    expectConfirmation(tx, 2)
  }

  it must "report concurrent transaction confirmations" in new Fixture {
    val otherTx = otherWallet.delegate.createSend(wallet.delegate.freshReceiveAddress(), 0.2.BTC)
    givenConfirmationSubscription(tx, 2)
    givenConfirmationSubscription(otherTx, 3)
    expectNoMsg()
    val block = sendToBlockChain(tx, otherTx)
    mineBlock()
    expectBlockHavingConfirmations(block, 2)
    expectConfirmation(tx, 2)
    mineBlock()
    expectBlockHavingConfirmations(block, 3)
    expectConfirmation(otherTx, 3)
  }

  it must "retrieve existing transaction in blockchain" in new Fixture {
    expectNoMsg()
    sendToBlockChain(tx)
    requester.send(instance, BlockchainActor.RetrieveTransaction(tx.getHash))
    requester.expectMsg(BlockchainActor.TransactionFound(tx.getHash, ImmutableTransaction(tx)))
  }

  it must "retrieve existing multisigned transaction in blockchain" in new Fixture {
    val (multisigTx, signatures) = createMultisigTransaction(0.2.BTC)
    requester.send(instance, BlockchainActor.WatchMultisigKeys(signatures))
    expectNoMsg()
    sendToBlockChain(multisigTx.get)
    requester.send(instance, BlockchainActor.RetrieveTransaction(multisigTx.get.getHash))
    requester.expectMsg(BlockchainActor.TransactionFound(multisigTx.get.getHash, multisigTx))
  }

  it must "fail to retrieve nonexistent transaction in blockchain" in new Fixture {
    requester.send(instance, BlockchainActor.RetrieveTransaction(tx.getHash))
    requester.expectMsg(BlockchainActor.TransactionNotFound(tx.getHash))
  }

  it must "report blockchain height after the blockchain reaches the notification threshold" in
    new Fixture {
      val targetHeight = chain.getBestChainHeight + 2
      requester.send(instance, BlockchainActor.WatchBlockchainHeight(targetHeight))
      expectNoMsg()
      mineBlock()
      expectNoMsg()
      mineBlock()
      requester.expectMsg(BlockchainActor.BlockchainHeightReached(targetHeight))
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

  it must "notify output expenditure" in new Fixture {
    sendToBlockChain(tx)
    val output = outputWithValue(tx, 0.1.BTC)
    requester.send(instance, BlockchainActor.WatchOutput(output))
    expectNoMsg()
    val spendTx = ImmutableTransaction{
      val tx = new MutableTransaction(network)
      tx.addInput(output)
      tx.addOutput(0.05.BTC, new KeyPair().toAddress(network))
      otherWallet.delegate.signTransaction(SendRequest.forTx(tx))
      tx
    }
    sendToBlockChain(spendTx.get)
    requester.expectMsg(BlockchainActor.OutputSpent(output, spendTx))

    withClue("notify when it is already spent") {
      requester.send(instance, BlockchainActor.WatchOutput(output))
      requester.expectMsg(BlockchainActor.OutputSpent(output, spendTx))
    }
  }

  trait Fixture {
    protected val requester = TestProbe()
    protected val wallet, otherWallet = new SmartWallet(createWallet(1.BTC), TransactionSizeFeeCalculator)
    protected val tx = createSendTransaction(0.1.BTC)

    protected val instance = system.actorOf(Props(new BlockchainActor(chain, wallet.delegate)))

    protected def createSendTransaction(amount: BitcoinAmount): MutableTransaction =
      wallet.delegate.createSend(otherWallet.delegate.freshReceiveAddress(), amount)

    protected def createMultisigTransaction(
        amount: BitcoinAmount): (ImmutableTransaction, Both[PublicKey]) = {
      val signatures = Both(
        buyer = wallet.delegate.freshReceiveKey(),
        seller = otherWallet.delegate.freshReceiveKey()
      )
      (wallet.createMultisignTransaction(signatures, 0.1.BTC), signatures.map(_.publicKey))
    }

    protected def expectNoMsg(): Unit = {
      requester.expectNoMsg(100.millis.dilated)
    }

    protected def outputWithValue(tx: MutableTransaction, value: BitcoinAmount) =
      tx.getOutputs.asScala.find(_.getValue.value == value.units).get

    protected def givenConfirmationSubscription(
        tx: MutableTransaction, confirmations: Int): Unit = {
      requester.send(instance,
        BlockchainActor.WatchTransactionConfirmation(tx.getHash, confirmations))
      expectNoMsg()
    }

    protected def expectAlreadyConfirmed(tx: MutableTransaction, confirmations: Int): Unit ={
      requester.send(instance,
        BlockchainActor.WatchTransactionConfirmation(tx.getHash, confirmations))
      expectConfirmation(tx, confirmations)
    }

    protected def expectConfirmation(tx: MutableTransaction, confirmations: Int): Unit = {
      requester.expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, confirmations))
    }

    class Branch {
      private var head = chainHead()

      def mineBlock(txs: MutableTransaction*): Unit = {
        head = sendToBlockChain(head, new KeyPair, txs: _*)
      }
    }
  }
}
