package coinffeine.gui.application.properties

import scala.concurrent.ExecutionContext

import org.bitcoinj.core.ECKey
import org.bitcoinj.params.TestNet3Params
import org.joda.time.DateTime
import org.scalatest.Inside
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Span, Seconds, Millis}

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.model.properties.{MutablePropertyMap, Property}
import coinffeine.peer.amounts.DefaultAmountsComponent
import coinffeine.peer.api.CoinffeineNetwork

class PeerOrdersTest extends UnitTest with Eventually with Inside {

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(5, Millis)))

  "Peer orders" should "have no elements upon construction" in new Fixture {
    orders shouldBe 'empty
  }

  it should "add a new element when new order is present in the network" in new Fixture {
    val orderId = OrderId("order-01")
    val order = Order(orderId, Bid, 1.BTC, Price(100.EUR))
    network.orders.set(orderId, order)
    eventually {
      inside(orders.toSeq) { case Seq(orderProps) =>
        orderProps.idProperty.get shouldBe orderId
      }
    }
  }

  it should "add a new element when multiple orders are present in the network" in new Fixture {
    withNewOrder("order-01") { order1 =>
      withNewOrder("order-02") { order2 =>
        eventually {
          inside(orders.toSeq.sortBy(_.idProperty.value.value)) {
            case Seq(orderProps1, orderProps2) =>
              orderProps1.idProperty.get shouldBe order1.id
              orderProps2.idProperty.get shouldBe order2.id
          }
        }
      }
    }
  }

  it should "replace an existing element when order is modified in the network" in new Fixture {
    withNewOrder("order-01") { order =>
      network.orders.set(order.id, order.withExchange(randomExchange(order)))
      eventually {
        inside(orders.toSeq) { case Seq(orderProps) =>
          orderProps.statusProperty.value shouldBe InProgressOrder
        }
      }
    }
  }

  it should "not replace an existing element until new exchanges are consolidated" in new Fixture {
    withNewOrder("order-01") { order =>
      val exchange = randomExchange(order)
      network.orders.set(order.id, order.withExchange(exchange))
      println(order.withExchange(exchange).amounts.progressMade)
      eventually {
        inside(orders.toSeq) { case Seq(orderProps) =>
          orderProps.statusProperty.value shouldBe InProgressOrder
          orderProps.exchanges shouldBe 'empty
        }
      }
      network.orders.set(order.id, order.withExchange(randomlyHandshake(exchange)))
      eventually {
        inside(orders.toSeq) {  case Seq(orderProps) =>
          orderProps.statusProperty.value shouldBe InProgressOrder
          inside(orderProps.exchanges.toSeq) { case Seq(ex) =>
            ex.exchangeIdProperty.value shouldBe exchange.id
          }
        }
      }
    }
  }

  it should "sort orders by the time of their last event" in new Fixture {
    val time1 = DateTime.now()
    val time2 = time1.plusMinutes(1)
    withNewOrder("order2", time2) { order2 =>
      withNewOrder("order1", time1) { order1 =>
        orders.toList.map(_.idProperty.get.value) shouldBe List("order1", "order2")
      }
    }
  }

  trait Fixture extends DefaultAmountsComponent {

    val network = new CoinffeineNetwork {
      override def cancelOrder(order: OrderId) = {}
      override def submitOrder[C <: FiatCurrency](order: Order[C]) = order
      override val brokerId: Property[Option[PeerId]] = null
      override val activePeers: Property[Int] = null
      override val orders: MutablePropertyMap[OrderId, AnyCurrencyOrder] =
        new MutablePropertyMap[OrderId, AnyCurrencyOrder]
    }

    val orders = new PeerOrders(network, ExecutionContext.global)

    def withNewOrder(id: String, timestamp: DateTime = DateTime.now())
                    (action: Order[Euro.type] => Unit): Unit = {
      val orderId = OrderId(id)
      val order = Order(orderId, Bid, 1.BTC, Price(100.EUR), timestamp)
      network.orders.set(orderId, order)
      eventually {
        orders.find(_.idProperty.get == orderId) shouldBe 'defined
      }
      action(order)
    }

    def randomExchange(order: Order[Euro.type]) = {
      val id = ExchangeId.random()
      val counterpart = PeerId.random()
      val amounts = amountsCalculator.estimateAmountsFor(order)
      val params = Exchange.Parameters(100, TestNet3Params.get())
      Exchange.create(id, BuyerRole, counterpart, amounts, params, DateTime.now())
    }

    def randomlyHandshake(exchange: HandshakingExchange[Euro.type]) = exchange.handshake(
      user = Exchange.PeerInfo("peer-01", new ECKey()),
      counterpart = Exchange.PeerInfo("peer-02", new ECKey()),
      timestamp = DateTime.now()
    )
  }
}
