package coinffeine.protocol.gateway.p2p

import java.net.{InetSocketAddress, NetworkInterface}
import scala.collection.JavaConverters._

import com.typesafe.scalalogging.StrictLogging
import net.tomp2p.connection.Bindings

private class BindingsBuilder(acceptedInterfaces: Seq[NetworkInterface]) extends StrictLogging {

  def defaultBindings(): Bindings = whitelistInterfaces(new Bindings())

  def bindToAddress(address: InetSocketAddress): Bindings =
    whitelistInterfaces(new Bindings(address.getAddress, address.getPort, address.getPort))

  private def whitelistInterfaces(bindings: Bindings): Bindings = {
    acceptedInterfaces.map(_.getName).foreach(bindings.addInterface)
    val ifaces = bindings.getInterfaces.asScala.mkString(",")
    logger.info("Enabled network interfaces [{}]", ifaces)
    bindings
  }
}
