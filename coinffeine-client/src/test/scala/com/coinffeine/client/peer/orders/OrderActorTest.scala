package com.coinffeine.client.peer.orders

import akka.actor.Props
import akka.testkit.TestProbe

import com.coinffeine.client.api.CoinffeineApp
import com.coinffeine.client.peer.orders.SubmissionSupervisor.{KeepSubmitting, StopSubmitting}
import com.coinffeine.common.{Ask, OrderBookEntry}
import com.coinffeine.common.Currency.Implicits._
import com.coinffeine.common.test.AkkaSpec

class OrderActorTest extends AkkaSpec {

  "An order actor" should "keep order info" in new Fixture {
    actor ! OrderActor.RetrieveStatus
    expectMsg(order)
  }

  it should "keep submitting to the broker until been cancelled" in new Fixture {
    submissionProbe.expectMsg(KeepSubmitting(order))
    expectNoMsg()
    actor ! OrderActor.CancelOrder
    submissionProbe.expectMsg(StopSubmitting(order.id))
  }

  it should "notify order creation and cancellation" in new Fixture {
    eventChannelProbe.expectMsg(CoinffeineApp.OrderSubmittedEvent(order))
    actor ! OrderActor.CancelOrder
    eventChannelProbe.expectMsg(CoinffeineApp.OrderCancelledEvent(order.id))
  }

  trait Fixture {
    val messageGatewayProbe = TestProbe()
    val eventChannelProbe = TestProbe()
    val actor = system.actorOf(Props(new OrderActor))
    val order = OrderBookEntry(Ask, 5.BTC, 500.EUR)
    val submissionProbe = TestProbe()

    actor ! OrderActor.Initialize(
      order, submissionProbe.ref, eventChannelProbe.ref, messageGatewayProbe.ref)
  }
}
