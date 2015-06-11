package coinffeine.model.network

import coinffeine.common.properties._

trait CoinffeineNetworkProperties {
  val activePeers: Property[Int]
  val brokerId: Property[Option[PeerId]]

  def isConnected: Boolean = activePeers.get > 0 && brokerId.get.isDefined
}
