package coinffeine.common.test

import scala.reflect.ClassTag

import akka.actor.{ActorRef, Props, ActorSystem}
import akka.testkit.TestProbe

/** Utility class for testing supervisor actors */
class MockSupervisedActor(implicit system: ActorSystem) {

  val probe = TestProbe()

  val props: Props = Props(new MockActor(probe.ref))

  def ref: ActorRef = refOpt.getOrElse(throw new Error("Mock was not yet created"))

  def expectCreation(): Unit = {
    val started = probe.expectMsgClass(classOf[MockActor.MockStarted])
    refOpt = Some(started.ref)
  }

  def expectMsg(message: Any): Unit = {
    probe.expectMsgPF() {
      case MockActor.MockReceived(_, _, `message`) =>
    }
  }

  def expectMsgType[T: ClassTag]: T = {
    val msg = probe.expectMsgType[MockActor.MockReceived]
    msg.message.asInstanceOf[T]
  }

  def expectForward(message: Any, expectedSender: ActorRef): Unit = {
    probe.expectMsgPF() {
      case MockActor.MockReceived(_, `expectedSender`, `message`) =>
    }
  }

  def expectAskWithReply(reply: PartialFunction[Any, Any]): Unit = {
    probe.expectMsgPF() {
      case MockActor.MockReceived(_, sender, message) if reply.isDefinedAt(message) =>
        sender ! reply(message)
    }
  }

  private var refOpt: Option[ActorRef] = None
}
