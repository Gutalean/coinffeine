package com.coinffeine.client.peer.orders

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor._
import akka.pattern._
import akka.util.Timeout

import com.coinffeine.client.peer.CoinffeinePeerActor._
import com.coinffeine.common.{FiatAmount, FiatCurrency, Order}
import com.coinffeine.common.exchange.PeerId
import com.coinffeine.common.protocol.ProtocolConstants
import com.coinffeine.common.protocol.messages.brokerage.Market

/** Manages open orders */
class OrdersActor(protocolConstants: ProtocolConstants) extends Actor with ActorLogging {

  import context.dispatcher

  override def receive: Receive = {
    case init: OrdersActor.Initialize =>
      new InitializedOrdersActor(init).start()
  }

  private class InitializedOrdersActor(init: OrdersActor.Initialize) {
    import init._

    private var delegatesByMarket = Map.empty[Market[FiatCurrency], ActorRef]

    def start(): Unit = {
      context.become(waitingForOrders)
    }

    private val waitingForOrders: Receive = {

      case message @ OpenOrder(order) =>
        getOrCreateDelegate(marketOf(order)) forward message

      case message @ CancelOrder(order) =>
        getOrCreateDelegate(marketOf(order)) forward message

      case RetrieveOpenOrders =>
        val listener = sender()
        for (orders <- collectOpenOrders()) {
          listener ! RetrievedOpenOrders(orders)
        }
    }

    private def marketOf(order: Order[FiatAmount]) = Market(currency = order.price.currency)

    private def getOrCreateDelegate(market: Market[FiatCurrency]): ActorRef =
      delegatesByMarket.getOrElse(market, createDelegate(market))

    private def createDelegate(market: Market[FiatCurrency]): ActorRef = {
      log.info(s"Start submitting to $market")
      val newDelegate = context.actorOf(OrderSubmissionActor.props(protocolConstants))
      newDelegate ! OrderSubmissionActor.Initialize(market, eventChannel, gateway, brokerId)
      delegatesByMarket += market -> newDelegate
      newDelegate
    }

    private def collectOpenOrders(): Future[Set[Order[FiatAmount]]] = {
      implicit val timeout = Timeout(1.second)
      for {
        results <- Future.sequence(delegatesByMarket.values.map { ref =>
          (ref ? RetrieveOpenOrders).mapTo[Set[Order[FiatAmount]]]
        })
      } yield results.foldLeft(Set.empty[Order[FiatAmount]])(_ union _)
    }
  }
}

object OrdersActor {

  case class Initialize(ownId: PeerId,
                        brokerId: PeerId,
                        eventChannel: ActorRef,
                        gateway: ActorRef)

  trait Component { this: ProtocolConstants.Component =>
    lazy val ordersActorProps = Props(new OrdersActor(protocolConstants))
  }
}
