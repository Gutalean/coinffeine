package coinffeine.peer.bitcoin

import akka.actor.Props
import akka.testkit.TestProbe
import org.scalatest.mock.MockitoSugar

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.bitcoin.test.BitcoinjTest
import coinffeine.model.bitcoin.{ImmutableTransaction, KeyPair}
import coinffeine.model.currency.Implicits._

class BlockchainActorTest extends AkkaSpec("BlockChainActorTest")
    with BitcoinjTest with MockitoSugar {

  "The blockchain actor" must "report transaction confirmation" in new Fixture {
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(tx.getHash, 1))
    sendToBlockChain(tx)
    requester.expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, 1))
  }

  it must "report multisigned transaction confirmation" in new Fixture {
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(multisignedTx.getHash, 1))
    sendToBlockChain(multisignedTx)
    requester.expectMsg(BlockchainActor.TransactionConfirmed(multisignedTx.getHash, 1))
  }

  it must "report transaction confirmation only once" in new Fixture {
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(tx.getHash, 2))
    sendToBlockChain(tx)
    mineBlock()
    requester.expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, 2))
    mineBlock()
    requester.expectNoMsg()
  }

  it must "not report transaction confirmation when still unconfirmed" in new Fixture {
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(tx.getHash, 3))
    sendToBlockChain(tx)
    requester.expectNoMsg()
  }

  it must "report transaction rejection when it's lost from the blockchain" in new Fixture {
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(tx.getHash, 3))
    val forkBlock = chainHead()
    sendToBlockChain(tx)
    val forkPlusOneBlock = sendToBlockChain(forkBlock, otherKeyPair)
    sendToBlockChain(forkPlusOneBlock, otherKeyPair)
    requester.expectMsg(BlockchainActor.TransactionRejected(tx.getHash))
  }

  it must "report transaction confirmation after blockchain fork including the tx" in new Fixture {
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(tx.getHash, 2))
    val forkBlock = chainHead()
    sendToBlockChain(tx)
    val forkPlusOneBlock = sendToBlockChain(forkBlock, otherKeyPair, tx)
    sendToBlockChain(forkPlusOneBlock, otherKeyPair)
    requester.expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, 2))
  }

  it must "report transaction confirmation after an attempt of blockchain fork" in new Fixture {
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(tx.getHash, 2))
    val forkBlock = chainHead()
    val origPlusOneBlock = sendToBlockChain(tx)
    val forkPlusOneBlock = sendToBlockChain(forkBlock, otherKeyPair, tx)
    sendToBlockChain(origPlusOneBlock, otherKeyPair)
    requester.expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, 2))
  }

  it must "report concurrent transaction confirmations" in new Fixture {
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(tx.getHash, 2))
    requester.send(instance, BlockchainActor.WatchTransactionConfirmation(otherTx.getHash, 3))
    sendToBlockChain(tx, otherTx)
    mineBlock()
    requester.expectMsg(BlockchainActor.TransactionConfirmed(tx.getHash, 2))
    mineBlock()
    requester.expectMsg(BlockchainActor.TransactionConfirmed(otherTx.getHash, 3))
  }

  it must "retrieve existing transaction in blockchain" in new Fixture {
    requester.send(instance, BlockchainActor.WatchPublicKey(keyPair))
    sendToBlockChain(tx)
    requester.send(instance, BlockchainActor.RetrieveTransaction(tx.getHash))
    requester.expectMsg(BlockchainActor.TransactionFound(tx.getHash, ImmutableTransaction(tx)))
  }

  it must "retrieve existing multisigned transaction in blockchain" in new Fixture {
    requester.send(instance, BlockchainActor.WatchMultisigKeys(Seq(keyPair, otherKeyPair)))
    sendToBlockChain(multisignedTx)
    requester.send(instance, BlockchainActor.RetrieveTransaction(multisignedTx.getHash))
    requester.expectMsg(BlockchainActor.TransactionFound(
      multisignedTx.getHash, ImmutableTransaction(multisignedTx)))
  }

  it must "fail to retrieve nonexistent transaction in blockchain" in new Fixture {
    requester.send(instance, BlockchainActor.WatchPublicKey(keyPair))
    requester.send(instance, BlockchainActor.RetrieveTransaction(tx.getHash))
    requester.expectMsg(BlockchainActor.TransactionNotFound(tx.getHash))
  }

  it must "report blockchain height after the blockchain reaches the notification threshold" in
    new Fixture {
      requester.send(instance, BlockchainActor.WatchBlockchainHeight(50))
      for (currentHeight <- chain.getBestChainHeight to 48) {
        mineBlock()
      }
      requester.expectNoMsg()
      mineBlock()
      requester.expectMsg(BlockchainActor.BlockchainHeightReached(50))
    }

  it must "retrieve the blockchain height" in new Fixture {
    requester.send(instance, BlockchainActor.RetrieveBlockchainHeight)
    requester.expectMsg(BlockchainActor.BlockchainHeightReached(chain.getBestChainHeight))
  }

  trait Fixture {
    val keyPair = new KeyPair()
    val otherKeyPair = new KeyPair()
    val wallet = new SmartWallet(createWallet(keyPair, 1.BTC))
    val transactionBroadcaster = new MockTransactionBroadcaster()
    val otherWallet = new SmartWallet(createWallet(keyPair, 1.BTC))
    val tx = wallet.delegate.createSend(keyPair.toAddress(network), 0.1.BTC.asSatoshi)
    val immutableTx = ImmutableTransaction(tx)
    val otherTx = otherWallet.delegate.createSend(keyPair.toAddress(network), 0.1.BTC.asSatoshi)
    val multisignedTx = otherWallet.blockMultisignFunds(Seq(keyPair, otherKeyPair), 0.1.BTC)
    val requester = TestProbe()

    val instance = system.actorOf(Props(new BlockchainActor(chain, network)))
  }
}
