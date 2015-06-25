package coinffeine.gui.application.properties

import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls

import org.bitcoinj.core.ECKey
import org.bitcoinj.params.TestNet3Params
import org.joda.time.DateTime
import org.scalatest.Inside
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}

import coinffeine.common.properties.MutablePropertyMap
import coinffeine.common.test.UnitTest
import coinffeine.model.currency._
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.model.order._
import coinffeine.peer.amounts.DefaultAmountsCalculator
import coinffeine.peer.api.CoinffeineOperations

class PeerOrdersTest extends UnitTest with Eventually with Inside {

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(5, Millis)))

  "Peer orders" should "have no elements upon construction" in new Fixture {
    orders shouldBe 'empty
  }

  it should "add a new element when new order is present in the network" in new Fixture {
    val orderId = OrderId("order-01")
    val order = ActiveOrder(orderId, Bid, 1.BTC, Price(100.EUR))
    operations.orders.set(orderId, order)
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
      operations.orders.set(order.id, order.withExchange(randomExchange(order)))
      eventually {
        inside(orders.toSeq) { case Seq(orderProps) =>
          orderProps.statusProperty.value shouldBe OrderStatus.InProgress
        }
      }
    }
  }

  it should "not replace an existing element until new exchanges are consolidated" in new Fixture {
    withNewOrder("order-01") { order =>
      val exchange = randomExchange(order)
      operations.orders.set(order.id, order.withExchange(exchange))
      println(order.withExchange(exchange).amounts.progressMade)
      eventually {
        inside(orders.toSeq) { case Seq(orderProps) =>
          orderProps.statusProperty.value shouldBe OrderStatus.InProgress
          orderProps.exchanges shouldBe 'empty
        }
      }
      operations.orders.set(order.id, order.withExchange(randomlyHandshake(exchange)))
      eventually {
        inside(orders.toSeq) {  case Seq(orderProps) =>
          orderProps.statusProperty.value shouldBe OrderStatus.InProgress
          inside(orderProps.exchanges.toSeq) { case Seq(ex) =>
            ex.exchangeIdProperty.value shouldBe exchange.id
          }
        }
      }
    }
  }

  trait Fixture {

    val operations = new CoinffeineOperations {
      override def cancelOrder(order: OrderId) = {}
      override def submitOrder(request: OrderRequest) =
        Future.successful(request.create())
      override val orders: MutablePropertyMap[OrderId, Order] =
        new MutablePropertyMap[OrderId, Order]
    }

    val orders = new PeerOrders(operations, ExecutionContext.global)

    def withNewOrder(id: String)(action: ActiveOrder => Unit): Unit = {
      val orderId = OrderId(id)
      val order = ActiveOrder(orderId, Bid, 1.BTC, Price(100.EUR))
      operations.orders.set(orderId, order)
      eventually {
        orders.find(_.idProperty.get == orderId) shouldBe 'defined
      }
      action(order)
    }

    def randomExchange(order: ActiveOrder) = {
      val id = ExchangeId.random()
      val counterpart = PeerId.random()
      val request = OrderRequest(order.orderType, order.amount, order.price)
      val amounts = new DefaultAmountsCalculator().estimateAmountsFor(request, Spread.empty).get
      val params = ActiveExchange.Parameters(100, TestNet3Params.get())
      ActiveExchange.create(id, BuyerRole, counterpart, amounts, params, DateTime.now())
    }

    def randomlyHandshake(exchange: HandshakingExchange) = exchange.handshake(
      user = Exchange.PeerInfo("peer-01", new ECKey()),
      counterpart = Exchange.PeerInfo("peer-02", new ECKey()),
      timestamp = DateTime.now()
    )
  }
}
