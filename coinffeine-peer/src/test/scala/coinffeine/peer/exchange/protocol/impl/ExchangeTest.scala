package coinffeine.peer.exchange.protocol.impl

import coinffeine.model.bitcoin.test.{BitcoinjTest, CoinffeineUnitTestNetwork}
import coinffeine.model.bitcoin.{ImmutableTransaction, Wallet}
import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange.{Both, RunningExchange}
import coinffeine.peer.exchange.protocol._

/** Base trait for testing the default exchange protocol */
trait ExchangeTest extends BitcoinjTest {

  def balance(wallet: Wallet): BitcoinAmount = Bitcoin.fromSatoshi(wallet.getBalance)

  def valueSent(tx: ImmutableTransaction, wallet: Wallet): BitcoinAmount =
    Bitcoin.fromSatoshi(tx.get.getValueSentToMe(wallet))

  /** Fixture with just a fresh protocol object */
  trait FreshInstance extends SampleExchange with CoinffeineUnitTestNetwork.Component {
    val protocol = new DefaultExchangeProtocol()
  }

  /** Fixture with a buyer handshake with the right amount of funds */
  trait BuyerHandshake extends FreshInstance {
    val buyerWallet = createWallet(participants.buyer.bitcoinKey, 0.2.BTC)
    val buyerFunds = UnspentOutput.collect(0.2.BTC, buyerWallet)
    val buyerHandshake =
      protocol.createHandshake(buyerExchange, buyerFunds, buyerWallet.getChangeAddress)
  }

  /** Fixture with a seller handshake with the right amount of funds */
  trait SellerHandshake extends FreshInstance {
    val sellerWallet = createWallet(participants.seller.bitcoinKey, 1.1.BTC)
    val sellerFunds = UnspentOutput.collect(1.1.BTC, sellerWallet)
    val sellerHandshake =
      protocol.createHandshake(sellerExchange, sellerFunds, sellerWallet.getChangeAddress)
  }

  /** Fixture with buyer and seller channels created after a successful handshake */
  trait Channels extends BuyerHandshake with SellerHandshake {
    private val commitments = Both(
      buyer = buyerHandshake.myDeposit,
      seller = sellerHandshake.myDeposit
    )
    sendToBlockChain(commitments.toSeq.map(_.get): _*)
    val deposits = protocol.validateDeposits(commitments, buyerExchange).get
    val buyerRunningExchange = RunningExchange(deposits, buyerExchange)
    val sellerRunningExchange = RunningExchange(deposits, sellerExchange)
    val buyerChannel = protocol.createMicroPaymentChannel(buyerRunningExchange)
    val sellerChannel = protocol.createMicroPaymentChannel(sellerRunningExchange)
    val totalSteps = exchange.amounts.breakdown.totalSteps
    val buyerChannels = Seq.iterate(buyerChannel, totalSteps)(_.nextStep)
    val sellerChannels = Seq.iterate(sellerChannel, totalSteps)(_.nextStep)
  }
}
