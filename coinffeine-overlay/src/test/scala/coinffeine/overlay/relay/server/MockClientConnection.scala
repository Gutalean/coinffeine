package coinffeine.overlay.relay.server

import java.net.InetSocketAddress
import scala.concurrent.duration.FiniteDuration

import akka.actor.ActorRef
import akka.io.Tcp
import akka.testkit.TestProbe
import akka.util.ByteString
import org.scalatest.{Inside, ShouldMatchers}

import coinffeine.overlay.OverlayId
import coinffeine.overlay.relay.messages._

class MockClientConnection(address: InetSocketAddress,
                           connectionProbe: TestProbe,
                           handler: ActorRef) extends ShouldMatchers with Inside {
  var id: OverlayId = null
  var lastStatus: Option[StatusMessage] = None

  def identifyAs(newId: OverlayId): Unit = {
    id = newId
    sendData(ProtobufFrame.serialize(JoinMessage(id)))
    inside(expectValidMessage()) {
      case StatusMessage(_) =>
    }
  }

  def unsuccessfullyIdentifyAs(newId: OverlayId): Unit = {
    id = newId
    sendData(ProtobufFrame.serialize(JoinMessage(id)))
    expectDisconnection()
  }

  def sendMessage(to: MockClientConnection, message: String): Unit = sendMessage(to.id, message)

  def sendMessage(to: OverlayId, message: String): Unit = {
    sendData(ProtobufFrame.serialize(RelayMessage(to, ByteString(message))))
  }

  def sendStatusMessage(): Unit = {
    sendData(ProtobufFrame.serialize(StatusMessage(3)))
  }

  def sendMalformedFrame(): Unit = {
    sendData(ByteString("not a valid frame"))
  }

  def sendFrameOfSize(bytes: Int): Unit = {
    val payload = Array.fill[Byte](bytes - Frame.HeaderSize)(0)
    sendData(Frame(ByteString(payload)).serialize)
  }

  def sendMalformedProtobuf(): Unit = {
    sendFrame(ByteString("not a valid protobuf"))
  }

  private def sendFrame(payload: ByteString): Unit = sendData(Frame(payload).serialize)

  private def sendData(data: ByteString): Unit = {
    // Simulate chunking
    data.grouped(10).foreach { chunk =>
      connectionProbe.send(handler, Tcp.Received(chunk))
    }
  }

  def expectMessage(from: MockClientConnection, message: String): Unit =
    expectMessage(from.id, message)

  def expectMessage(from: OverlayId, message: String): Unit = {
    expectValidMessage() shouldBe RelayMessage(from, ByteString(message))
  }

  def expectStatusUpdate(networkSize: Int): Unit = {
    expectValidMessage() shouldBe StatusMessage(networkSize)
  }

  def expectNoMsg(time: FiniteDuration): Unit = {
    connectionProbe.expectNoMsg(time)
  }

  private def expectValidMessage(): Message = {
    val message = ProtobufConversion.fromProtobuf(expectValidFrame().payload)
    message match {
      case message: StatusMessage => lastStatus = Some(message)
      case _ =>
    }
    message
  }

  private def expectValidFrame(): Frame = {
    val data = connectionProbe.expectMsgType[Tcp.Write].data
    val Frame.Parsed(frame, _) = Frame.deserialize(data, Int.MaxValue)
    frame
  }

  def disconnect(): Unit = {
    connectionProbe.send(handler, Tcp.Closed)
  }

  def expectDisconnection(): Unit = {
    connectionProbe.expectMsg(Tcp.Close)
    connectionProbe.reply(Tcp.Closed)
  }
}
