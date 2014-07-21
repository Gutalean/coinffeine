package coinffeine.common.test

import akka.actor.{ActorRef, Props, ActorSystem}
import akka.testkit.TestProbe
import org.scalatest.Assertions

/** Utility class for testing supervisor actors */
class MockSupervisedActor(implicit system: ActorSystem) extends Assertions {

  val probe = TestProbe()

  val props: Props = Props(new MockActor(probe.ref))

  def ref: ActorRef = refOpt.getOrElse(fail("Mock was not yet created"))

  def expectCreation(): Unit = {
    val started = probe.expectMsgClass(classOf[MockActor.MockStarted])
    refOpt = Some(started.ref)
  }

  def expectMsg(message: Any): Unit = {
    probe.expectMsgPF() {
      case MockActor.MockReceived(_, _, `message`) =>
    }
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
