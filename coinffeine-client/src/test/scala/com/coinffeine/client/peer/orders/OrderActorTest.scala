package com.coinffeine.client.peer.orders

import akka.actor.Props
import akka.testkit.TestProbe

import com.coinffeine.client.peer.orders.SubmissionSupervisor.{KeepSubmitting, StopSubmitting}
import com.coinffeine.common.{Ask, Order}
import com.coinffeine.common.Currency.Implicits._
import com.coinffeine.common.test.AkkaSpec

class OrderActorTest extends AkkaSpec {

  "An order actor" should "keep order info" in new Fixture {
    actor ! OrderActor.Initialize(order, submissionProbe.ref)
    actor ! OrderActor.RetrieveStatus
    expectMsg(order)
  }

  it should "keep submitting to the broker until been cancelled" in new Fixture {
    actor ! OrderActor.Initialize(order, submissionProbe.ref)
    submissionProbe.expectMsg(KeepSubmitting(order))
    expectNoMsg()
    actor ! OrderActor.CancelOrder
    submissionProbe.expectMsg(StopSubmitting(order.id))
  }

  trait Fixture {
    val actor = system.actorOf(Props(new OrderActor))
    val order = Order(Ask, 5.BTC, 500.EUR)
    val submissionProbe = TestProbe()
  }
}
