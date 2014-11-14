package coinffeine.model.network

import java.net.{InetAddress, InetSocketAddress}

import scala.concurrent.{ExecutionContext, Future}

case class NetworkEndpoint(hostname: String, port: Int) {

  override def toString = s"$hostname:$port"

  def resolve()(implicit executor: ExecutionContext): Future[InetSocketAddress] = Future {
    new InetSocketAddress(InetAddress.getByName(hostname), port)
  }
}
