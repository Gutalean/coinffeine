package coinffeine.common.test

import scala.util.Random

/** Default instance of TcpPortAllocator */
object DefaultTcpPortAllocator extends TcpPortAllocator(Random.nextInt(64511) + 1024)
