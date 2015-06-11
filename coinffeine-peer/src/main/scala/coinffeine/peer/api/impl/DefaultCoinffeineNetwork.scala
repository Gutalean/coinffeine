package coinffeine.peer.api.impl

import coinffeine.common.properties.Property
import coinffeine.model.network.{CoinffeineNetworkProperties, PeerId}
import coinffeine.peer.api.CoinffeineNetwork

private[impl] class DefaultCoinffeineNetwork(properties: CoinffeineNetworkProperties)
  extends CoinffeineNetwork {

  override val activePeers: Property[Int] = properties.activePeers
  override val brokerId: Property[Option[PeerId]] = properties.brokerId
}
