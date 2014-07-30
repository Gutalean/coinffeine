package coinffeine.peer.exchange.protocol.impl

import coinffeine.model.bitcoin.Implicits._
import coinffeine.model.bitcoin.test.{BitcoinjTest, CoinffeineUnitTestNetwork}
import coinffeine.model.bitcoin.{ImmutableTransaction, Wallet}
import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange._

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
    val buyerDeposit = ImmutableTransaction(buyerWallet.blockMultisignFunds(
      requiredSignatures, BuyerRole.myDepositAmount(amounts)))
    val buyerHandshake = protocol.createHandshake(buyerHandshakingExchange, buyerDeposit)
  }

  /** Fixture with a seller handshake with the right amount of funds */
  trait SellerHandshake extends FreshInstance {
    val sellerWallet = createWallet(participants.seller.bitcoinKey, 1.1.BTC)
    val sellerDeposit = ImmutableTransaction(sellerWallet.blockMultisignFunds(
      requiredSignatures, SellerRole.myDepositAmount(amounts)))
    val sellerHandshake = protocol.createHandshake(sellerHandshakingExchange, sellerDeposit)
  }

  /** Fixture with buyer and seller channels created after a successful handshake */
  trait Channels extends BuyerHandshake with SellerHandshake {
    private val commitments = Both(
      buyer = buyerHandshake.myDeposit,
      seller = sellerHandshake.myDeposit
    )
    sendToBlockChain(commitments.toSeq.map(_.get): _*)
    val deposits = protocol.validateDeposits(commitments, buyerHandshakingExchange).get
    val buyerRunningExchange = RunningExchange(deposits, buyerHandshakingExchange)
    val sellerRunningExchange = RunningExchange(deposits, sellerHandshakingExchange)
    val buyerChannel = protocol.createMicroPaymentChannel(buyerRunningExchange)
    val sellerChannel = protocol.createMicroPaymentChannel(sellerRunningExchange)
    val totalSteps = buyerExchange.amounts.breakdown.totalSteps
    val buyerChannels = Seq.iterate(buyerChannel, totalSteps)(_.nextStep)
    val sellerChannels = Seq.iterate(sellerChannel, totalSteps)(_.nextStep)
  }
}
