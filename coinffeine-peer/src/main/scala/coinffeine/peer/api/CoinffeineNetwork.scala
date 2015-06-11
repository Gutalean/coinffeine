package coinffeine.peer.api

import coinffeine.model.network.CoinffeineNetworkProperties

/** Represents how the app takes part on the P2P network */
trait CoinffeineNetwork extends CoinffeineNetworkProperties

object CoinffeineNetwork {

  case class ConnectException(cause: Throwable)
    extends Exception("Cannot connect to the P2P network", cause)
}
