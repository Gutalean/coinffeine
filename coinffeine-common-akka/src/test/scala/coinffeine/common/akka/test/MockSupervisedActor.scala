package coinffeine.common.akka.test

import scala.reflect.ClassTag

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import org.scalatest.ShouldMatchers

import coinffeine.common.akka.test.MockActor.{MockRestarted, MockStopped, MockThrow}

/** Utility class for testing supervisor actors */
class MockSupervisedActor(implicit system: ActorSystem) extends ShouldMatchers {

  val probe = TestProbe()

  def props(args: Any*): Props = Props(new MockActor(probe.ref, args))

  def ref: ActorRef = refOpt.getOrElse(throw new Error("Mock was not yet created"))

  def expectCreation(): Seq[Any] = {
    val started = probe.expectMsgType[MockActor.MockStarted]
    refOpt = Some(started.ref)
    started.args
  }

  def expectRestart(): Unit = {
    probe.expectMsgClass(classOf[MockStopped])
    probe.expectMsgClass(classOf[MockRestarted])
  }

  def expectStop(): Unit = {
    probe.expectMsgClass(classOf[MockStopped])
    probe.expectNoMsg()
  }

  def expectNoMsg(): Unit = {
    probe.expectNoMsg()
  }

  def expectMsg(message: Any): Unit = {
    probe.expectMsgPF() {
      case MockActor.MockReceived(_, _, `message`) =>
    }
  }

  def expectMsgPF[T](pattern: PartialFunction[Any, T]): Unit = {
    probe.expectMsgPF() {
      case MockActor.MockReceived(_, _, message) if pattern.isDefinedAt(message) => pattern(message)
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

  def throwException(exception: Throwable): Unit = {
    ref ! MockThrow(exception)
  }

  private var refOpt: Option[ActorRef] = None
}
