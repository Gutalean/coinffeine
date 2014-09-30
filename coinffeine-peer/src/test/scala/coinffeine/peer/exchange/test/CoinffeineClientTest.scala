package coinffeine.peer.exchange.test

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.currency.Euro
import coinffeine.model.exchange._
import coinffeine.model.network.{BrokerId, PeerId}
import coinffeine.peer.exchange.protocol._
import coinffeine.protocol.gateway.MessageGateway.ReceiveMessage
import coinffeine.protocol.gateway.MockGateway
import coinffeine.protocol.messages.PublicMessage

abstract class CoinffeineClientTest(systemName: String)
  extends AkkaSpec(systemName) with SampleExchange {

  val gateway = new MockGateway(PeerId("broker"))

  def fromBroker(message: PublicMessage) = ReceiveMessage(message, BrokerId)
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
    def counterpartId = exchange.counterpartId
    def fromCounterpart(message: PublicMessage) = ReceiveMessage(message, counterpartId)
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
