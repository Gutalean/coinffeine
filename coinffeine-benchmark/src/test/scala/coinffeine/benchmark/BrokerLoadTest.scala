package coinffeine.benchmark

import scala.concurrent.duration._

import io.gatling.core.Predef._

import coinffeine.benchmark.action.Predef._
import coinffeine.benchmark.config.Predef._
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.protocol.messages.brokerage.{OpenOrders, OpenOrdersRequest}

class BrokerLoadTest extends Simulation {

  val market = Market(Euro)

  val orderBookEntries = Seq(
    OrderBookEntry.random(Bid, 10.BTC, Price(200.EUR)),
    OrderBookEntry.random(Bid, 5.BTC, Price(250.EUR)),
    OrderBookEntry.random(Bid, 1.BTC, Price(300.EUR)),
    OrderBookEntry.random(Ask, 1.BTC, Price(400.EUR)),
    OrderBookEntry.random(Ask, 5.BTC, Price(450.EUR)),
    OrderBookEntry.random(Ask, 10.BTC, Price(500.EUR))
  )

  val coinffeineConf = coinffeineProtocol
    .brokerEndpoint("dev.coinffeine.com", 9009)

  val requestOpenOrders = exec(ask("RequestOpenOrders")
    .message(OpenOrdersRequest(market))
    .response { case _: OpenOrders[_] => })

  val putMyOrders = exec(putOrders("PutMyOrders")
    .orderBookEntries(orderBookEntries))

  val standbyPeer = repeat(20) {
    exec(requestOpenOrders)
    .pause(5)
    .exec(putMyOrders)
    .pause(5)
  }

  val scn = scenario("StandbyPeer")
    .exec(standbyPeer)

  setUp(scn.inject(constantUsersPerSec(2) during (25 seconds)))
    .protocols(coinffeineConf)
}
