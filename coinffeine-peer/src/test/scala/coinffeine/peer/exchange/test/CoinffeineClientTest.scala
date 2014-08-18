package coinffeine.peer.exchange.test

import akka.testkit.TestProbe

import coinffeine.common.akka.{ServiceRegistry, ServiceRegistryActor}
import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.currency.Currency.Euro
import coinffeine.model.exchange._
import coinffeine.model.network.PeerId
import coinffeine.peer.exchange.protocol._
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway.{ForwardMessage, ReceiveMessage}
import coinffeine.protocol.messages.PublicMessage

abstract class CoinffeineClientTest(systemName: String)
  extends AkkaSpec(systemName) with SampleExchange {

  val registryActor = system.actorOf(ServiceRegistryActor.props())
  val registry = new ServiceRegistry(registryActor)
  val gateway = TestProbe()
  registry.register(MessageGateway.ServiceId, gateway.ref)

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
    val exchange: NonStartedExchange[Euro.type]
    def participants: Both[Exchange.PeerInfo]
    def handshakingExchange = exchange.startHandshaking(user, counterpart)
    def runningExchange = handshakingExchange.startExchanging(MockExchangeProtocol.DummyDeposits)
    def completedExchange = runningExchange.complete
    def user = exchange.role.select(participants)
    def counterpart = exchange.role.counterpart.select(participants)
    def counterpartConnection = exchange.counterpartId
    def fromCounterpart(message: PublicMessage) = ReceiveMessage(message, counterpartConnection)
  }

  trait BuyerPerspective extends Perspective {
    override lazy val exchange = buyerExchange
    def buyerExchange: NonStartedExchange[Euro.type]
  }

  trait SellerPerspective extends Perspective {
    override lazy val exchange = sellerExchange
    def sellerExchange: NonStartedExchange[Euro.type]
  }
}
