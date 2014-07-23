package coinffeine.peer.api.impl

import akka.actor.{ActorSystem, Props}

import coinffeine.model.payment.PaymentProcessor
import coinffeine.peer.CoinffeinePeerActor
import coinffeine.peer.api._
import coinffeine.peer.config.ConfigComponent
import coinffeine.peer.event.EventObserverActor

/** Implements the coinffeine application API as an actor system. */
class DefaultCoinffeineApp(accountId: PaymentProcessor.AccountId,
                           peerProps: Props) extends CoinffeineApp {

  private val system = ActorSystem()
  private val peerRef = system.actorOf(peerProps, "peer")

  override val network = new DefaultCoinffeineNetwork(peerRef)

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
    this: CoinffeinePeerActor.Component with ConfigComponent =>

    private val accountId = config.getString("coinffeine.okpay.id")

    override lazy val app = new DefaultCoinffeineApp(accountId, peerProps)
  }
}
