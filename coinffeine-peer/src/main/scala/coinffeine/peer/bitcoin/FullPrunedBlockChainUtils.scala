package coinffeine.peer.bitcoin

import java.util.concurrent.ThreadPoolExecutor
import scala.util.control.NonFatal

import com.google.bitcoin.core.FullPrunedBlockChain
import org.slf4j.LoggerFactory

private[bitcoin] object FullPrunedBlockChainUtils {

  /** Ugly hack to stop the thread pool at [[com.google.bitcoin.core.FullPrunedBlockChain]] that
    * otherwise will prevent our application from closing.
    */
  def shutdown(blockchain: FullPrunedBlockChain): Unit = try {
    val field = blockchain.getClass.getDeclaredField("scriptVerificationExecutor")
    field.setAccessible(true)
    field.get(blockchain).asInstanceOf[ThreadPoolExecutor].shutdown()
  } catch {
    case NonFatal(ex) => Log.error("Cannot stop the thread pool at the blockchain object", ex)
  }

  private val Log = LoggerFactory.getLogger(FullPrunedBlockChainUtils.getClass)
}
