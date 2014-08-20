package coinffeine.peer.api.impl

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import akka.actor.{ActorSystem, Props}
import akka.util.Timeout

import coinffeine.common.akka.{ServiceActor, AskPattern}
import coinffeine.model.payment.PaymentProcessor
import coinffeine.peer.CoinffeinePeerActor
import coinffeine.peer.api._
import coinffeine.model.event.CoinffeineAppEvent
import coinffeine.peer.config.ConfigComponent
import coinffeine.peer.event.EventObserverActor

/** Implements the coinffeine application API as an actor system.
  *
  * @constructor
  * @param name       Name used for the actor system (useful for making sense of actor refs)
  * @param accountId  Payment processor account to use
  * @param peerProps  Props to start the main actor
  */
class DefaultCoinffeineApp(name: String,
                           accountId: PaymentProcessor.AccountId,
                           peerProps: Props) extends CoinffeineApp {

  private val system = ActorSystem(name)
  private val peerRef = system.actorOf(peerProps, "peer")

  override val network = new DefaultCoinffeineNetwork(peerRef)

  override lazy val wallet = new DefaultCoinffeineWallet(peerRef)

  override val marketStats = new DefaultMarketStats(peerRef)

  override val paymentProcessor = new DefaultCoinffeinePaymentProcessor(accountId, peerRef)

  override def close(): Unit = system.shutdown()

  override def observe(handler: EventHandler): Unit = {
    val observer = system.actorOf(EventObserverActor.props(handler))
    system.eventStream.subscribe(observer, classOf[CoinffeineAppEvent])
  }

  override def start()(implicit timeout: FiniteDuration): Future[Unit] = {
    import system.dispatcher
    implicit val to = Timeout(timeout)
    AskPattern(peerRef, ServiceActor.Start {})
      .withReply[Any]()
        .collect {
          case ServiceActor.Started =>
          case ServiceActor.StartFailure(cause) =>
            throw new RuntimeException("cannot start coinffeine app", cause)
      }
  }
}

object DefaultCoinffeineApp {
  trait Component extends CoinffeineAppComponent {
    this: CoinffeinePeerActor.Component with ConfigComponent =>

    private val accountId = config.getString("coinffeine.okpay.id")

    override lazy val app = new DefaultCoinffeineApp(name = accountId, accountId, peerProps)
  }
}
