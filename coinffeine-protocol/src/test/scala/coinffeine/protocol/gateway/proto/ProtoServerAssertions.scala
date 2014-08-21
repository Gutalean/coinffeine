package coinffeine.protocol.gateway.proto

import akka.actor.ActorRef
import akka.testkit.TestProbe
import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.event.CoinffeineConnectionStatus
import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.MessageGateway.RetrieveConnectionStatus
import org.scalatest.concurrent.{IntegrationPatience, Eventually}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration._

trait ProtoServerAssertions extends Eventually with IntegrationPatience { this: AkkaSpec =>

  val connectionTimeout = 30.seconds

  def waitForConnections(ref: ActorRef, minConnections: Int): PeerId = {
    val pollingProbe = TestProbe()
    eventually(timeout = Timeout(connectionTimeout)) {
      pollingProbe.send(ref, RetrieveConnectionStatus)
      pollingProbe.expectMsgPF(hint =
        s"waiting for knowing the broker and having $minConnections connections") {
        case CoinffeineConnectionStatus(connections, Some(brokerId))
          if connections >= minConnections => brokerId
      }
    }
  }
}
