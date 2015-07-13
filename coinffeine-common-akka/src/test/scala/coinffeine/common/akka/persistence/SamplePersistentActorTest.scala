package coinffeine.common.akka.persistence

import akka.actor.Props

import coinffeine.common.akka.persistence.SamplePersistentActor.{AddNumber, RequestSum, Sum}
import coinffeine.common.akka.test.AkkaSpec

class SamplePersistentActorTest extends AkkaSpec() {

  "A sample persistent actor" should "do something" in {
    val actor = system.actorOf(Props(new SamplePersistentActor("id1")))
    actor ! AddNumber(1)
    actor ! AddNumber(2)
    actor ! AddNumber(3)
    actor ! RequestSum
    expectMsg(Sum(6))
    system.stop(actor)
  }

  it should "recover previous status and carry on" in {
    val actor = system.actorOf(Props(new SamplePersistentActor("id1")))
    actor ! AddNumber(4)
    actor ! RequestSum
    expectMsg(Sum(10))
    system.stop(actor)
  }
}
