package coinffeine.protocol.gateway.proto

import com.google.protobuf.RpcCallback

object Callbacks {
  def noop[T]: RpcCallback[T] = new RpcCallback[T] { def run(parameter: T): Unit = {} }
}
