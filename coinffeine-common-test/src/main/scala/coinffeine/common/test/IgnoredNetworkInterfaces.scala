package coinffeine.common.test

import java.net.NetworkInterface

trait IgnoredNetworkInterfaces {

  /** A relation of network interfaces to be ignored to avoid routing problems while testing. */
  lazy val ignoredNetworkInterfaces = Option(NetworkInterface.getByName("utun0")).toSeq
}
