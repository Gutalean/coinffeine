package com.coinffeine.client.peer

import akka.testkit.TestProbe
import org.scalatest.mock.MockitoSugar

import com.coinffeine.client.api.CoinffeineApp
import com.coinffeine.common.test.AkkaSpec

class EventChannelActorTest extends AkkaSpec with MockitoSugar {

  val channel = system.actorOf(EventChannelActor.props())
  val event = mock[CoinffeineApp.Event]

  "Event channel actor" must "forward events to its subscribers" in {
    val subs = givenSubscribers(2)
    channel ! event
    subs.foreach(_.expectMsg(event))
  }

  it must "stop forwarding events after unsubscription" in {
    val subs = givenSubscribers(2)
    subs.head.send(channel, CoinffeinePeerActor.Unsubscribe)
    channel ! event
    subs.head.expectNoMsg()
    subs.last.expectMsg(event)
  }

  private def givenSubscribers(numberOfSubscribers: Int): Set[TestProbe] = {
    val subs = Set(TestProbe(), TestProbe())
    subs.foreach(_.send(channel, CoinffeinePeerActor.Subscribe))
    subs
  }
}
