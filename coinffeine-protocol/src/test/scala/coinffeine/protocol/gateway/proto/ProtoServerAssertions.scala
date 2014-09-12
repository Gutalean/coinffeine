package coinffeine.protocol.gateway.proto

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.network.{CoinffeineNetworkProperties, PeerId}
import org.scalatest.concurrent.{IntegrationPatience, Eventually}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration._

trait ProtoServerAssertions extends Eventually with IntegrationPatience { this: AkkaSpec =>

  val connectionTimeout = 30.seconds

  def waitForConnections(properties: CoinffeineNetworkProperties, minConnections: Int): PeerId = {
    eventually(timeout = Timeout(connectionTimeout)) {
      properties.activePeers.get should be >= minConnections
      properties.brokerId.get should be ('defined)
    }
    properties.brokerId.get.get
  }
}
