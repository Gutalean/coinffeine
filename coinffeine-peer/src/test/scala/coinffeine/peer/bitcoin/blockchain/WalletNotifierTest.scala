package coinffeine.peer.bitcoin.blockchain

import scala.concurrent.Promise

import org.bitcoinj.core.Wallet

import coinffeine.common.test.FutureMatchers
import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.bitcoin.test.BitcoinjTest
import coinffeine.model.currency._

class WalletNotifierTest extends BitcoinjTest with FutureMatchers {

  "A wallet notifier" should "notify when an output is spent" in new Fixture {
    notifier.watchOutput(outpoint, listener)
    listener.future should not be 'completed
    val tx = spendAllFunds()
    listener.future.futureValue shouldBe tx
  }

  it should "not notify when not subscribed" in new Fixture {
    val tx = spendAllFunds()
    Thread.sleep(100)
    listener.future should not be 'completed
  }

  private trait Fixture {
    protected val wallet = createWallet(1.BTC)
    protected val notifier = new WalletNotifier
    wallet.addEventListener(notifier)

    protected val outpoint = wallet.calculateAllSpendCandidates(false).getFirst.getOutPointFor
    protected val listener = new MockListener

    protected def spendAllFunds(): ImmutableTransaction = {
      val tx = wallet.sendCoinsOffline(Wallet.SendRequest.emptyWallet(wallet.getChangeAddress))
      sendToBlockChain(tx)
      ImmutableTransaction(tx)
    }
  }

  private class MockListener extends WalletNotifier.OutputListener {
    private val transactionPromise = Promise[ImmutableTransaction]()
    def future = transactionPromise.future
    override def outputSpent(tx: ImmutableTransaction): Unit = transactionPromise.success(tx)
  }
}
