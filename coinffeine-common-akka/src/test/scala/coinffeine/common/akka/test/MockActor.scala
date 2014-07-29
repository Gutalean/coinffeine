package coinffeine.common.akka.test

import akka.actor._
import akka.testkit.TestProbe

/** This actor allow to forward messages and spy what it receives
  *
  * @param master who is informed about the actor activity
  */
class MockActor(master: ActorRef) extends Actor with ActorLogging {
  import MockActor._

  override def preStart(): Unit = { master ! MockStarted(self) }
  override def postStop(): Unit = { master ! MockStopped(self) }
  override def postRestart(reason: Throwable): Unit = {
    master ! MockRestarted(self, reason)
  }

  def receive: Actor.Receive = {
    case MockSend(target, message) => target ! message
    case MockThrow(ex) => throw ex
    case message if sender != master =>
      master ! MockReceived(self, sender(), message)
    case unexpectedMessage =>
      log.warning("Unexpected message {} received by a mock actor", unexpectedMessage)
  }
}

object MockActor {
  def props(master: ActorRef): Props = Props(new MockActor(master))
  def props(probe: TestProbe): Props = props(probe.ref)

  /** Notification to the master when the mock is started.
    *
    * @param ref The reference of the newly created actor
    */
  case class MockStarted(ref: ActorRef)

  /** Notification to the master when the mock is stopped.
    *
    * @param ref The reference of the stopped actor
    */
  case class MockStopped(ref: ActorRef)

  /** Notification to the master when the mock is restarted */
  case class MockRestarted(ref: ActorRef, reason: Throwable)

  /** Notification sen to the master of the mock receiving any message.
    *
    * @param ref      The mock actor
    * @param sender   Who is communicating with the mock
    * @param message  Received message
    */
  case class MockReceived(ref: ActorRef, sender: ActorRef, message: Any)

  /** Message sent to the mock to make it resent as own */
  case class MockSend(target: ActorRef, message: Any)

  /** Message sent to the mock to make it fail with an exception */
  case class MockThrow(ex: Throwable)
}
