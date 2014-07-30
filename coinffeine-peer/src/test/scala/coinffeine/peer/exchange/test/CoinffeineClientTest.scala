package coinffeine.peer.exchange.test

import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange._
import coinffeine.model.network.PeerId
import coinffeine.peer.exchange.protocol._
import coinffeine.protocol.gateway.MessageGateway.{ForwardMessage, ReceiveMessage}
import coinffeine.protocol.messages.PublicMessage

abstract class CoinffeineClientTest(systemName: String)
  extends AkkaSpec(systemName) with SampleExchange {

  val gateway = TestProbe()

  def fromBroker(message: PublicMessage) = ReceiveMessage(message, brokerId)

  protected class ValidateWithPeer(validation: PeerId => Unit) {
    def to(receiver: PeerId): Unit = validation(receiver)
  }

  def shouldForward(message: PublicMessage) =
    new ValidateWithPeer(receiver => gateway.expectMsg(ForwardMessage(message, receiver)))

  protected class ValidateAllMessagesWithPeer {
    private var messages: List[PeerId => Any] = List.empty
    def message(msg: PublicMessage): ValidateAllMessagesWithPeer = {
      messages = ((receiver: PeerId) => ForwardMessage(msg, receiver)) :: messages
      this
    }
    def to(receiver: PeerId): Unit = {
      gateway.expectMsgAllOf(messages.map(_(receiver)): _*)
    }
  }

  def shouldForwardAll = new ValidateAllMessagesWithPeer
}

object CoinffeineClientTest {

  trait Perspective {
    val exchange: Exchange[FiatCurrency]
    def participants: Both[Exchange.PeerInfo]
    def handshakingExchange = HandshakingExchange(user, counterpart, exchange)
    def runningExchange = RunningExchange(MockExchangeProtocol.DummyDeposits, handshakingExchange)
    def userRole: Role
    def user = userRole.select(participants)
    def counterpart = userRole.counterpart.select(participants)
    def counterpartConnection = exchange.counterpartId
    def fromCounterpart(message: PublicMessage) = ReceiveMessage(message, counterpartConnection)
  }

  trait BuyerPerspective extends Perspective {
    override val userRole = BuyerRole
    override lazy val exchange = buyerExchange
    def buyerExchange: Exchange[FiatCurrency]
  }

  trait SellerPerspective extends Perspective {
    override val userRole = SellerRole
    override lazy val exchange = sellerExchange
    def sellerExchange: Exchange[FiatCurrency]
  }
}
