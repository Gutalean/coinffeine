package coinffeine.peer.exchange.protocol.impl

import coinffeine.model.Both
import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.bitcoin.test.{BitcoinjTest, CoinffeineUnitTestNetwork}
import coinffeine.model.currency.Bitcoin
import coinffeine.model.exchange._
import coinffeine.peer.bitcoin.wallet.SmartWallet

/** Base trait for testing the default exchange protocol */
trait ExchangeTest extends BitcoinjTest {

  def valueSent(tx: ImmutableTransaction, wallet: SmartWallet): Bitcoin.Amount =
    tx.get.getValueSentToMe(wallet.delegate)

  /** Fixture with just a fresh protocol object */
  trait FreshInstance extends SampleExchange with CoinffeineUnitTestNetwork.Component {
    val protocol = new DefaultExchangeProtocol()
  }

  /** Fixture with a buyer handshake with the right amount of funds */
  trait BuyerHandshake extends FreshInstance {
    val buyerWallet = new SmartWallet(
      createWallet(participants.buyer.bitcoinKey, amounts.bitcoinRequired.buyer))
    val buyerDeposit = {
      val depositAmounts = amounts.deposits.buyer
      buyerWallet.createMultisignTransaction(
        requiredSignatures, depositAmounts.output, depositAmounts.fee)
    }
    val buyerHandshake = protocol.createHandshake(buyerHandshakingExchange, buyerDeposit)
  }

  /** Fixture with a seller handshake with the right amount of funds */
  trait SellerHandshake extends FreshInstance {
    val sellerWallet = new SmartWallet(
      createWallet(participants.seller.bitcoinKey, amounts.bitcoinRequired.seller))
    val sellerDeposit = {
      val depositAmounts = amounts.deposits.seller
      sellerWallet.createMultisignTransaction(
        requiredSignatures, depositAmounts.output, depositAmounts.fee)
    }
    val sellerHandshake = protocol.createHandshake(sellerHandshakingExchange, sellerDeposit)
  }

  /** Fixture with buyer and seller channels created after a successful handshake */
  trait Channels extends BuyerHandshake with SellerHandshake {
    private val commitments = Both(
      buyer = buyerHandshake.myDeposit,
      seller = sellerHandshake.myDeposit
    )
    sendToBlockChain(commitments.toSeq.map(_.get): _*)
    private val validation = protocol.validateDeposits(
      commitments, amounts, requiredSignatures, parameters.network)
    require(validation.forall(_.isSuccess))
    val buyerRunningExchange =
      buyerHandshakingExchange.startExchanging(commitments, ExchangeTimestamps.channelStart)
    val sellerRunningExchange =
      sellerHandshakingExchange.startExchanging(commitments, ExchangeTimestamps.channelStart)
    val buyerChannel = protocol.createMicroPaymentChannel(buyerRunningExchange)
    val sellerChannel = protocol.createMicroPaymentChannel(sellerRunningExchange)
    val totalSteps = buyerExchange.amounts.breakdown.totalSteps
    val buyerChannels = Seq.iterate(buyerChannel, totalSteps)(_.nextStep)
    val sellerChannels = Seq.iterate(sellerChannel, totalSteps)(_.nextStep)
  }
}
