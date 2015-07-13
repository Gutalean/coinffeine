package coinffeine.common.akka.event

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.TestProbe

class EventProbe(topic: String)(implicit system: ActorSystem) extends TestProbe(system) {

  private val eventConsumer = system.actorOf(Props(new Actor with CoinffeineEventConsumer {

    override def preStart() = subscribe(topic)

    override def receive = { case event => ref forward event }
  }))
}
