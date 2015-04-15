package coinffeine.peer.exchange.test

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.Both
import coinffeine.model.currency.Euro
import coinffeine.model.exchange._
import coinffeine.peer.exchange.protocol._
import coinffeine.protocol.gateway.MessageGateway.ReceiveMessage
import coinffeine.protocol.gateway.MockGateway
import coinffeine.protocol.messages.PublicMessage

abstract class CoinffeineClientTest(systemName: String)
  extends AkkaSpec(systemName) with SampleExchange {

  val gateway = new MockGateway()
}

object CoinffeineClientTest {

  trait Perspective extends SampleExchange {
    val exchange: HandshakingExchange[Euro.type]
    def myRole: Role
    def participants: Both[Exchange.PeerInfo]
    def handshakingExchange =
      exchange.handshake(user, counterpart, ExchangeTimestamps.handshakingStart)
    def runningExchange = handshakingExchange.startExchanging(
      MockExchangeProtocol.DummyDeposits, ExchangeTimestamps.channelStart)
    def completedExchange = runningExchange.complete(ExchangeTimestamps.completion)
    def user = exchange.role.select(participants)
    def counterpart = exchange.role.counterpart.select(participants)
    def counterpartId = exchange.counterpartId
    def fromCounterpart(message: PublicMessage) = ReceiveMessage(message, counterpartId)
  }

  trait BuyerPerspective extends Perspective {
    override lazy val exchange = buyerExchange
    override def myRole = BuyerRole
    def buyerExchange: HandshakingExchange[Euro.type]
  }

  trait SellerPerspective extends Perspective {
    override lazy val exchange = sellerExchange
    override def myRole = SellerRole
    def sellerExchange: HandshakingExchange[Euro.type]
  }
}
