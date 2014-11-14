package coinffeine.model.network

import java.net.{InetAddress, InetSocketAddress}

import scala.concurrent.{ExecutionContext, Future}

case class NetworkEndpoint(hostname: String, port: Int) {

  require(!hostname.isEmpty, "cannot create a network endpoint with an empty hostname")

  override def toString = s"$hostname:$port"

  def resolve()(implicit executor: ExecutionContext): Future[InetSocketAddress] = Future {
    new InetSocketAddress(InetAddress.getByName(hostname), port)
  }
}
