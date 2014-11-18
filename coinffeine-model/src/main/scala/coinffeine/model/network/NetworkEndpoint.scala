package coinffeine.model.network

import java.io.IOException
import java.net.{InetAddress, InetSocketAddress}
import scala.concurrent.{ExecutionContext, Future}

case class NetworkEndpoint(hostname: String, port: Int) {

  require(!hostname.isEmpty, "cannot create a network endpoint with an empty hostname")

  override def toString = s"$hostname:$port"

  def resolveAsync()(implicit executor: ExecutionContext): Future[InetSocketAddress] =
    Future { resolve() }

  @throws[IOException]("When address cannot be resolved")
  def resolve(): InetSocketAddress = {
    new InetSocketAddress(InetAddress.getByName(hostname), port)
  }
}
