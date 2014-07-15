package com.coinffeine.client.app

import akka.actor.{ActorSystem, Props}

import com.coinffeine.client.api._
import com.coinffeine.client.peer.{CoinffeinePeerActor, EventObserverActor}
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
