package coinffeine.peer.bitcoin.blockchain

import scala.concurrent.duration._
import scala.collection.JavaConverters._

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
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(tx.getHash, 1))
    expectNoMsg()
    sendToBlockChain(tx)
    requester.expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, 1))
  }

  it must "report multisigned transaction confirmation" in new Fixture {
    val (multisigTx, _) = createMultisigTransaction(0.1.BTC)
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(multisigTx.get.getHash, 1))
    expectNoMsg()
    sendToBlockChain(multisigTx.get)
    requester.expectMsg(BlockchainActor.TransactionConfirmed(multisigTx.get.getHash, 1))
  }

  it must "report transaction confirmation when watching for it after the fact" in new Fixture {
    expectNoMsg()
    sendToBlockChain(tx)
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(tx.getHash, 1))
    requester.expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, 1))
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
    val alternativeBranch = new Branch
    sendToBlockChain(tx)
    alternativeBranch.mineBlock()
    alternativeBranch.mineBlock()
    requester.expectMsg(BlockchainActor.TransactionRejected(tx.getHash))
  }

  it must "report transaction confirmation after blockchain fork including the tx" in new Fixture {
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(tx.getHash, 2))
    expectNoMsg()
    val alternativeBranch = new Branch
    sendToBlockChain(tx)
    alternativeBranch.mineBlock(tx)
    alternativeBranch.mineBlock()
    requester.expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, 2))
  }

  it must "report transaction confirmation after an attempt of blockchain fork" in new Fixture {
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(tx.getHash, 2))
    expectNoMsg()
    val mainBranch, alternativeBranch = new Branch
    alternativeBranch.mineBlock(tx)
    mainBranch.mineBlock(tx)
    mainBranch.mineBlock()
    requester.expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, 2))
  }

  it must "report concurrent transaction confirmations" in new Fixture {
    val otherTx = otherWallet.delegate.createSend(wallet.delegate.freshReceiveAddress(), 0.2.BTC)
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
    val requester = TestProbe()
    val wallet, otherWallet = new SmartWallet(createWallet(1.BTC))
    val tx = createSendTransaction(0.1.BTC)

    val instance = system.actorOf(Props(new BlockchainActor(chain, wallet.delegate)))

    def createSendTransaction(amount: Bitcoin.Amount): MutableTransaction =
      wallet.delegate.createSend(otherWallet.delegate.freshReceiveAddress(), amount)

    def createMultisigTransaction(amount: Bitcoin.Amount): (ImmutableTransaction, Both[PublicKey]) = {
      val signatures = Both(
        buyer = wallet.delegate.freshReceiveKey(),
        seller = otherWallet.delegate.freshReceiveKey()
      )
      (wallet.createMultisignTransaction(signatures, 0.1.BTC), signatures.map(_.publicKey))
    }

    def expectNoMsg(): Unit = {
      requester.expectNoMsg(100.millis.dilated)
    }

    def outputWithValue(tx: MutableTransaction, value: Bitcoin.Amount) =
      tx.getOutputs.asScala.find(_.getValue.value == value.units).get

    class Branch {
      private var head = chainHead()

      def mineBlock(txs: MutableTransaction*): Unit = {
        head = sendToBlockChain(head, new KeyPair, txs: _*)
      }
    }
  }
}
