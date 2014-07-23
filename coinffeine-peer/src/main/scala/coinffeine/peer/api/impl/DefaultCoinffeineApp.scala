package coinffeine.peer.api.impl

import akka.actor.{ActorSystem, Props}

import coinffeine.model.network.PeerId
import coinffeine.model.payment.PaymentProcessor
import coinffeine.peer.api._
import coinffeine.peer.config.ConfigComponent
import coinffeine.peer.event.EventObserverActor
import coinffeine.peer.{CoinffeinePeerActor, ProtocolConstants}

/** Implements the coinffeine application API as an actor system. */
class DefaultCoinffeineApp(peerId: PeerId,
                           accountId: PaymentProcessor.AccountId,
                           peerProps: Props,
                           override val protocolConstants: ProtocolConstants)
  extends CoinffeineApp {

  private val system = ActorSystem()
  private val peerRef = system.actorOf(peerProps, "peer")

  override val network = new DefaultCoinffeineNetwork(peerId, peerRef)

  override lazy val wallet = new DefaultCoinffeineWallet(peerRef)

  override val marketStats = new DefaultMarketStats(peerRef)

  override val paymentProcessor = new DefaultCoinffeinePaymentProcessor(accountId, peerRef)

  override def close(): Unit = system.shutdown()

  override def observe(handler: EventHandler): Unit = {
    val observer = system.actorOf(EventObserverActor.props(handler))
    peerRef.tell(CoinffeinePeerActor.Subscribe, observer)
  }
}

object DefaultCoinffeineApp {
  trait Component extends CoinffeineAppComponent {
    this: CoinffeinePeerActor.Component with ProtocolConstants.Component with ConfigComponent =>

    private val peerId = PeerId(config.getString("coinffeine.peer.id"))
    private val accountId = config.getString("coinffeine.okpay.id")

    override lazy val app = new DefaultCoinffeineApp(peerId, accountId, peerProps, protocolConstants)
  }
}
