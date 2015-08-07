package coinffeine.peer.api.impl

import scala.concurrent.Future
import scala.util.control.{NoStackTrace, NonFatal}

import akka.actor._
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging

import coinffeine.common.akka.Service
import coinffeine.model.bitcoin.{BitcoinFeeCalculator, TransactionSizeFeeCalculator}
import coinffeine.peer.CoinffeinePeerActor
import coinffeine.peer.amounts.{AmountsCalculator, DefaultAmountsCalculator}
import coinffeine.peer.api._
import coinffeine.peer.config.{ConfigComponent, ConfigProvider}
import coinffeine.peer.properties.alarms.EventObservedAlarmsProperty
import coinffeine.peer.properties.bitcoin.{DefaultNetworkProperties, DefaultWalletProperties}
import coinffeine.peer.properties.fiat.DefaultPaymentProcessorProperties
import coinffeine.peer.properties.operations.DefaultOperationsProperties
import coinffeine.protocol.properties.DefaultCoinffeineNetworkProperties

/** Implements the coinffeine application API as an actor system. */
class DefaultCoinffeineApp(
    name: String,
    peerProps: Props,
    amountsCalculator: AmountsCalculator,
    bitcoinFeeCalculator: BitcoinFeeCalculator,
    configProvider: ConfigProvider) extends CoinffeineApp with LazyLogging {

  private implicit val system = ActorSystem(name, configProvider.enrichedConfig)
  private val peerRef = system.actorOf(peerProps, "peer")

  override val network = new DefaultCoinffeineNetwork(new DefaultCoinffeineNetworkProperties)

  override val operations = new DefaultCoinffeineOperations(new DefaultOperationsProperties, peerRef)

  override val bitcoinNetwork = new DefaultBitcoinNetwork(new DefaultNetworkProperties)

  override lazy val wallet = new DefaultCoinffeineWallet(new DefaultWalletProperties, peerRef)

  override val marketStats = new DefaultMarketStats(peerRef)

  override val paymentProcessor = new DefaultCoinffeinePaymentProcessor(
    configProvider, peerRef, new DefaultPaymentProcessorProperties)

  override val utils = new DefaultCoinffeineUtils(amountsCalculator, bitcoinFeeCalculator)

  override val alarms = new EventObservedAlarmsProperty

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

  trait Component extends CoinffeineAppComponent {
    this: CoinffeinePeerActor.Component with ConfigComponent =>

    override lazy val app = {
      val name = SystemName.choose(systemName)
      new DefaultCoinffeineApp(name, peerProps, new DefaultAmountsCalculator(),
        TransactionSizeFeeCalculator, configProvider)
    }

    private def systemName = configProvider.okPaySettings().userAccount
  }
}
