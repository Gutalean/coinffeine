package coinffeine.peer

import akka.actor.ActorSystem

import coinffeine.common.akka.{ServiceRegistry, ServiceRegistryActor}
import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.{MessageGateway, GatewayProbe}

class GlobalServices(implicit system: ActorSystem) {

  val registryActor = system.actorOf(ServiceRegistryActor.props())
  val registry = new ServiceRegistry(registryActor)

  val messageGateway = {
    val gateway = new GatewayProbe(PeerId("broker"))
    registry.register(MessageGateway.ServiceId, gateway.ref)
    gateway
  }
}
