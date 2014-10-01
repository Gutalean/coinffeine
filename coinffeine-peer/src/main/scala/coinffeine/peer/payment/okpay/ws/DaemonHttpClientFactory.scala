package coinffeine.peer.payment.okpay.ws

import java.util.{concurrent => juc}

import com.ning.http.client.providers.netty.NettyAsyncHttpProviderConfig
import com.ning.http.client.{AsyncHttpClient, AsyncHttpClientConfig}
import dispatch.{BuildInfo, DaemonThreads}
import org.jboss.netty.channel.socket.nio.{NioClientSocketChannelFactory, NioWorkerPool}
import org.jboss.netty.util.HashedWheelTimer

private[ws] object DaemonHttpClientFactory {

  def build(): AsyncHttpClient = {
    val timer = new HashedWheelTimer(DaemonThreads.factory)
    val shuttingDown = new juc.atomic.AtomicBoolean(false)

    object ThreadFactory extends juc.ThreadFactory {
      def newThread(runnable: Runnable) = new ShutdownOnInterruptThread(runnable)
    }

    object ChannelFactory extends NioClientSocketChannelFactory {
      val workerCount = 2 * Runtime.getRuntime.availableProcessors()
      new NioClientSocketChannelFactory(
        juc.Executors.newCachedThreadPool(ThreadFactory),
        1,
        new NioWorkerPool(
          juc.Executors.newCachedThreadPool(ThreadFactory),
          workerCount
        ),
        timer
      )
    }

    class ShutdownOnInterruptThread(runnable: Runnable) extends Thread(runnable) {
      setDaemon(true)

      override def interrupt(): Unit = {
        if (shuttingDown.compareAndSet(false, true)) {
          ChannelFactory.releaseExternalResources()
          timer.stop()
        }
        super.interrupt()
      }
    }

    val config = new NettyAsyncHttpProviderConfig()
      .addProperty(NettyAsyncHttpProviderConfig.SOCKET_CHANNEL_FACTORY, ChannelFactory)

    config.setNettyTimer(timer)

    new AsyncHttpClient(new AsyncHttpClientConfig.Builder()
      .setUserAgent("Dispatch/%s" format BuildInfo.version)
      .setRequestTimeoutInMs(-1)
      .setAsyncHttpClientProviderConfig(config)
      .build())
  }
}
