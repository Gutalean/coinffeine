package coinffeine.protocol.gateway

import scala.concurrent.duration._

import akka.testkit._

import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{Eventually, IntegrationPatience}

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.network.{CoinffeineNetworkProperties, PeerId}

trait MessageGatewayAssertions extends Eventually with IntegrationPatience { this: AkkaSpec =>

  val connectionTimeout = 5.seconds.dilated

  def waitForConnections(properties: CoinffeineNetworkProperties, minConnections: Int): PeerId = {
    eventually(timeout = Timeout(connectionTimeout)) {
      properties.activePeers.get should be >= minConnections
      properties.brokerId.get shouldBe 'defined
    }
    properties.brokerId.get.get
  }
}
