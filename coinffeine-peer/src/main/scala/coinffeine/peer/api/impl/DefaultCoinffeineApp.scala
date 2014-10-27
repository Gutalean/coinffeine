package coinffeine.peer.api.impl

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import coinffeine.common.akka.ServiceActor
import coinffeine.model.bitcoin.BitcoinProperties
import coinffeine.model.network.CoinffeineNetworkProperties
import coinffeine.model.payment.PaymentProcessor._
import coinffeine.peer.CoinffeinePeerActor
import coinffeine.peer.amounts.{AmountsCalculator, DefaultAmountsComponent}
import coinffeine.peer.api._
import coinffeine.peer.config.ConfigComponent
import coinffeine.peer.payment.PaymentProcessorProperties
import coinffeine.peer.properties.DefaultCoinffeinePropertiesComponent

/** Implements the coinffeine application API as an actor system. */
class DefaultCoinffeineApp(name: String,
                           properties: DefaultCoinffeineApp.Properties,
                           lookupAccountId: () => Option[AccountId],
                           peerProps: Props,
                           amountsCalculator: AmountsCalculator,
                           config: Config) extends CoinffeineApp {

  private val system = ActorSystem(name, config)
  private val peerRef = system.actorOf(peerProps, "peer")

  override val network = new DefaultCoinffeineNetwork(properties.network, peerRef)

  override val bitcoinNetwork = new DefaultBitcoinNetwork(properties.bitcoin.network)

  override lazy val wallet = new DefaultCoinffeineWallet(properties.bitcoin.wallet, peerRef)

  override val marketStats = new DefaultMarketStats(peerRef)

  override val paymentProcessor = new DefaultCoinffeinePaymentProcessor(
    lookupAccountId, peerRef, properties.paymentProcessor)

  override val utils = new DefaultCoinffeineUtils(amountsCalculator)

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
    }.map { _ =>
      system.shutdown()
      system.awaitTermination()
    }
  }
}

object DefaultCoinffeineApp {
  private val Log = LoggerFactory.getLogger(classOf[DefaultCoinffeineApp])

  case class Properties(bitcoin: BitcoinProperties,
                        network: CoinffeineNetworkProperties,
                        paymentProcessor: PaymentProcessorProperties)

  trait Component extends CoinffeineAppComponent {
    this: CoinffeinePeerActor.Component with ConfigComponent
      with DefaultAmountsComponent with DefaultCoinffeinePropertiesComponent =>

    private def accountId() = configProvider.okPaySettings().userAccount

    private val properties = Properties(
      bitcoinProperties, coinffeineNetworkProperties, paymentProcessorProperties)

    override lazy val app = new DefaultCoinffeineApp(
      name = chooseName(), properties, accountId, peerProps, amountsCalculator, configProvider.config)

    /** Choose a name use for naming the actor systems and logging */
    private def chooseName() = accountId().getOrElse("app-" + Random.nextInt(1000))
  }
}
