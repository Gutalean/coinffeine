package coinffeine.peer.api

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, Future}

import coinffeine.peer.global.GlobalProperties

/** Coinffeine application interface */
trait CoinffeineApp {

  def network: CoinffeineNetwork
  def bitcoinNetwork: BitcoinNetwork
  def wallet: CoinffeineWallet
  def paymentProcessor: CoinffeinePaymentProcessor
  def marketStats: MarketStats
  def utils: CoinffeineUtils
  def global: GlobalProperties

  def start(timeout: FiniteDuration): Future[Unit]
  def stop(timeout: FiniteDuration): Future[Unit]

  def startAndWait(timeout: FiniteDuration): Unit = waitForever(start(timeout))
  def stopAndWait(timeout: FiniteDuration): Unit = waitForever(stop(timeout))

  private def waitForever(future: Future[Unit]): Unit = {
    Await.result(future, Duration.Inf)
  }
}
