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

  def start(): Future[Unit]
  def stop(): Future[Unit]

  def startAndWait(): Unit = waitForever(start())
  def stopAndWait(): Unit = waitForever(stop())

  private def waitForever(future: Future[Unit]): Unit = {
    Await.result(future, Duration.Inf)
  }
}
