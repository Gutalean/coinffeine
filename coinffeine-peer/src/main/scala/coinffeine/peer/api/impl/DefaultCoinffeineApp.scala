package coinffeine.peer.api.impl

import scala.concurrent.Future
import scala.util.control.{NoStackTrace, NonFatal}

import akka.actor._
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging

import coinffeine.common.akka.Service
import coinffeine.model.bitcoin.BitcoinProperties
import coinffeine.model.network.CoinffeineNetworkProperties
import coinffeine.model.payment.PaymentProcessor._
import coinffeine.peer.CoinffeinePeerActor
import coinffeine.peer.amounts.{AmountsCalculator, DefaultAmountsComponent}
import coinffeine.peer.api._
import coinffeine.peer.config.{ConfigComponent, ConfigProvider}
import coinffeine.peer.global.GlobalProperties
import coinffeine.peer.payment.PaymentProcessorProperties
import coinffeine.peer.properties.DefaultCoinffeinePropertiesComponent

/** Implements the coinffeine application API as an actor system. */
class DefaultCoinffeineApp(name: String,
                           properties: DefaultCoinffeineApp.Properties,
                           lookupAccountId: () => Option[AccountId],
                           peerProps: Props,
                           amountsCalculator: AmountsCalculator,
                           configProvider: ConfigProvider) extends CoinffeineApp with LazyLogging {

  private val system = ActorSystem(name, configProvider.enrichedConfig)
  private val peerRef = system.actorOf(peerProps, "peer")

  override val network = new DefaultCoinffeineNetwork(properties.network, peerRef)

  override val bitcoinNetwork = new DefaultBitcoinNetwork(properties.bitcoin.network)

  override lazy val wallet = new DefaultCoinffeineWallet(properties.bitcoin.wallet, peerRef)

  override val marketStats = new DefaultMarketStats(peerRef)

  override val paymentProcessor = new DefaultCoinffeinePaymentProcessor(
    lookupAccountId, peerRef, properties.paymentProcessor)

  override val utils = new DefaultCoinffeineUtils(amountsCalculator)

  override val global = properties.global

  override def start(): Future[Unit] = {
    import system.dispatcher
    implicit val to = Timeout(configProvider.generalSettings().serviceStartStopTimeout)
    for {
      _ <- Service.askStart(peerRef).recoverWith {
        case NonFatal(cause) => Future.failed(new RuntimeException("cannot start coinffeine app", cause))
      }
      _ <- system.actorSelection("/system/journal").resolveOne().recoverWith {
        case ActorNotFound(_) => Future.failed(new RuntimeException(
          "Application data files cannot be locked. You might have already opened the Coinffeine app."
        ) with NoStackTrace)
      }
    } yield ()
  }

  override def stop(): Future[Unit] = {
    import system.dispatcher
    implicit val to = Timeout(configProvider.generalSettings().serviceStartStopTimeout)
    Service.askStop(peerRef).recover {
      case cause => logger.error("cannot gracefully stop coinffeine app", cause)
    }.map { _ =>
      system.shutdown()
      system.awaitTermination()
    }
  }
}

object DefaultCoinffeineApp {
  case class Properties(bitcoin: BitcoinProperties,
                        network: CoinffeineNetworkProperties,
                        paymentProcessor: PaymentProcessorProperties,
                        global: GlobalProperties)

  trait Component extends CoinffeineAppComponent {
    this: CoinffeinePeerActor.Component with ConfigComponent
      with DefaultAmountsComponent with DefaultCoinffeinePropertiesComponent =>

    private def accountId() = configProvider.okPaySettings().userAccount

    private val properties = Properties(
      bitcoinProperties, coinffeineNetworkProperties, paymentProcessorProperties, globalProperties)

    override lazy val app = {
      val name = SystemName.choose(accountId())
      new DefaultCoinffeineApp(
        name, properties, accountId, peerProps, amountsCalculator, configProvider)
    }
  }
}
