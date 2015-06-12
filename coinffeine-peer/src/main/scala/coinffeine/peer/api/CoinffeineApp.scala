package coinffeine.peer.api

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

import coinffeine.alarms.Alarm
import coinffeine.common.properties.Property

/** Coinffeine application interface */
trait CoinffeineApp {

  def network: CoinffeineNetwork
  def operations: CoinffeineOperations
  def bitcoinNetwork: BitcoinNetwork
  def wallet: CoinffeineWallet
  def paymentProcessor: CoinffeinePaymentProcessor
  def marketStats: MarketStats
  def utils: CoinffeineUtils

  def alarms: Property[Set[Alarm]]

  def start(): Future[Unit]
  def stop(): Future[Unit]

  def startAndWait(): Unit = waitForever(start())
  def stopAndWait(): Unit = waitForever(stop())

  private def waitForever(future: Future[Unit]): Unit = {
    Await.result(future, Duration.Inf)
  }
}
