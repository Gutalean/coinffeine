package coinffeine.common.akka.test

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.TestProbe
import org.scalatest.ShouldMatchers

import coinffeine.common.akka.test.MockActor.{MockRestarted, MockStopped, MockThrow}

/** Utility class for testing supervisor actors */
class MockSupervisedActor(implicit system: ActorSystem) extends ShouldMatchers {

  val probe = TestProbe()
  private val defaultTimeout = probe.testKitSettings.DefaultTimeout.duration

  def props(args: Any*): Props = Props(new MockActor(probe.ref, args))

  def ref: ActorRef = refOpt.getOrElse(throw new Error("Mock was not yet created"))

  def expectCreation(timeout: FiniteDuration = defaultTimeout): Seq[Any] = {
    val started = probe.expectMsgType[MockActor.MockStarted](timeout)
    refOpt = Some(started.ref)
    started.args
  }

  def expectRestart(): Unit = {
    probe.expectMsgType[MockStopped]
    probe.expectMsgType[MockRestarted]
  }

  def expectStop(timeout: FiniteDuration = defaultTimeout): Unit = {
    probe.expectMsgType[MockStopped](timeout)
  }

  def expectNoMsg(): Unit = {
    probe.expectNoMsg()
  }

  def expectMsg(message: Any): Unit = {
    probe.expectMsgPF(hint = message.toString) {
      case MockActor.MockReceived(_, _, `message`) =>
    }
  }

  def expectMsgPF[T](pattern: PartialFunction[Any, T], hint: String = ""): Unit = {
    probe.expectMsgPF(hint = hint) {
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

  def stop(): Unit = {
    ref ! PoisonPill
  }

  def throwException(exception: Throwable): Unit = {
    ref ! MockThrow(exception)
  }

  private var refOpt: Option[ActorRef] = None
}
