package com.coinffeine.client.peer.orders

import scala.concurrent.duration._

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout

import coinffeine.model.currency.{FiatAmount, FiatCurrency}
import coinffeine.model.market.{OrderBookEntry, OrderId}
import coinffeine.model.network.PeerId
import com.coinffeine.client.peer.orders.SubmissionSupervisor.{KeepSubmitting, StopSubmitting}
import com.coinffeine.common.protocol.ProtocolConstants
import com.coinffeine.common.protocol.messages.brokerage.Market

class SubmissionSupervisor(protocolConstants: ProtocolConstants) extends Actor with ActorLogging{
  private implicit val timeout = Timeout(1.second)

  override def receive: Receive = {
    case init: SubmissionSupervisor.Initialize =>
      new InitializedSubmissionSupervisor(init).start()
  }

  private class InitializedSubmissionSupervisor(init: SubmissionSupervisor.Initialize) {
    import init._

    private var delegatesByMarket = Map.empty[Market[FiatCurrency], ActorRef]

    def start(): Unit = {
      context.become(waitingForOrders)
    }

    private val waitingForOrders: Receive = {

      case message @ KeepSubmitting(order) =>
        getOrCreateDelegate(marketOf(order)) forward message

      case message @ StopSubmitting(order) =>
      delegatesByMarket.values.foreach(_ forward message)
    }

    private def marketOf(order: OrderBookEntry[FiatAmount]) = Market(currency = order.price.currency)

    private def getOrCreateDelegate(market: Market[FiatCurrency]): ActorRef =
    delegatesByMarket.getOrElse(market, createDelegate(market))

    private def createDelegate(market: Market[FiatCurrency]): ActorRef = {
      log.info(s"Start submitting to $market")
      val newDelegate = context.actorOf(MarketSubmissionActor.props(protocolConstants))
      newDelegate ! MarketSubmissionActor.Initialize(market, gateway, brokerId)
      delegatesByMarket += market -> newDelegate
      newDelegate
    }
  }
}

object SubmissionSupervisor {

  case class Initialize(brokerId: PeerId, gateway: ActorRef)

  case class KeepSubmitting(order: OrderBookEntry[FiatAmount])

  case class StopSubmitting(orderId: OrderId)

  trait Component { this: ProtocolConstants.Component =>
    lazy val submissionSupervisorProps = Props(new SubmissionSupervisor(protocolConstants))
  }
}
