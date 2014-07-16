package coinffeine.peer.api.impl

import akka.actor.{ActorSystem, Props}

import coinffeine.peer.CoinffeinePeerActor
import coinffeine.peer.api._
import coinffeine.peer.event.EventObserverActor
import com.coinffeine.common.ProtocolConstants
import com.coinffeine.common.paymentprocessor.PaymentProcessor

/** Implements the coinffeine application API as an actor system.
  *
  * FIXME: partial API implementation
  */
class DefaultCoinffeineApp(peerProps: Props, override val protocolConstants: ProtocolConstants)
  extends CoinffeineApp {

  private val system = ActorSystem()
  private val peerRef = system.actorOf(peerProps, "peer")

  override val network = new DefaultCoinffeineNetwork(peerRef)

  override lazy val wallet = ???

  override val marketStats = new DefaultMarketStats(peerRef)

  override val paymentProcessors: Set[PaymentProcessor.Component] = Set.empty

  override def close(): Unit = system.shutdown()

  override def observe(handler: EventHandler): Unit = {
    val observer = system.actorOf(EventObserverActor.props(handler))
    peerRef.tell(CoinffeinePeerActor.Subscribe, observer)
  }
}

object DefaultCoinffeineApp {
  trait Component extends CoinffeineAppComponent {
    this: CoinffeinePeerActor.Component with ProtocolConstants.Component =>

    override lazy val app = new DefaultCoinffeineApp(peerProps, protocolConstants)
  }
}
