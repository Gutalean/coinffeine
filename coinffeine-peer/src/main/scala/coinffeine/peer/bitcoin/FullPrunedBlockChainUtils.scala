package coinffeine.peer.bitcoin

import java.util.concurrent.ThreadPoolExecutor
import scala.util.control.NonFatal

import com.typesafe.scalalogging.LazyLogging
import org.bitcoinj.core.FullPrunedBlockChain

private[bitcoin] object FullPrunedBlockChainUtils extends LazyLogging {

  /** Ugly hack to stop the thread pool at [[org.bitcoinj.core.FullPrunedBlockChain]] that
    * otherwise will prevent our application from closing.
    */
  def shutdown(blockchain: FullPrunedBlockChain): Unit = try {
    val field = blockchain.getClass.getDeclaredField("scriptVerificationExecutor")
    field.setAccessible(true)
    field.get(blockchain).asInstanceOf[ThreadPoolExecutor].shutdown()
  } catch {
    case NonFatal(ex) => logger.error("Cannot stop the thread pool at the blockchain object", ex)
  }
}
