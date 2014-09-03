package coinffeine.peer.api.impl

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import org.slf4j.LoggerFactory

import coinffeine.common.akka.ServiceActor
import coinffeine.model.event.CoinffeineAppEvent
import coinffeine.model.payment.PaymentProcessor
import coinffeine.peer.CoinffeinePeerActor
import coinffeine.peer.amounts.{DefaultAmountsComponent, ExchangeAmountsCalculator}
import coinffeine.peer.api._
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
                           peerProps: Props,
                           amountsCalculator: ExchangeAmountsCalculator) extends CoinffeineApp {

  private val system = ActorSystem(name)
  private val peerRef = system.actorOf(peerProps, "peer")

  override val network = new DefaultCoinffeineNetwork(peerRef)

  override lazy val wallet = new DefaultCoinffeineWallet(peerRef)

  override val marketStats = new DefaultMarketStats(peerRef)

  override val paymentProcessor = new DefaultCoinffeinePaymentProcessor(accountId, peerRef)

  override val utils = new DefaultCoinffeineUtils(amountsCalculator)

  override def observe(handler: EventHandler): Unit = {
    val observer = system.actorOf(EventObserverActor.props(handler))
    system.eventStream.subscribe(observer, classOf[CoinffeineAppEvent])
  }

  override def start(timeout: FiniteDuration): Future[Unit] = {
    import system.dispatcher
    implicit val to = Timeout(timeout)
    ServiceActor.askStart(peerRef).recoverWith {
      case cause => Future.failed(new RuntimeException("cannot start coinffeine app", cause))
    }
  }

  override def stop(timeout: FiniteDuration): Future[Unit] = {
    import system.dispatcher
    implicit val to = Timeout(timeout)
    ServiceActor.askStop(peerRef).recover {
      case cause =>
        DefaultCoinffeineApp.Log.error("cannot gracefully stop coinffeine app", cause)
    }.map(_ => system.shutdown())
  }
}

object DefaultCoinffeineApp {
  private val Log = LoggerFactory.getLogger(classOf[DefaultCoinffeineApp])

  trait Component extends CoinffeineAppComponent {
    this: CoinffeinePeerActor.Component with ConfigComponent with DefaultAmountsComponent =>

    private val accountId = settingsProvider.config.getString("coinffeine.okpay.id")

    override lazy val app = new DefaultCoinffeineApp(
      name = accountId, accountId, peerProps, exchangeAmountsCalculator)
  }
}
