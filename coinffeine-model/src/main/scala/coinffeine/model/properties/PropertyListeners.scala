package coinffeine.model.properties

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

import com.typesafe.scalalogging.LazyLogging

class PropertyListeners[Handler] extends LazyLogging {

  private case class Listener(handler: Handler, executor: ExecutionContext)
      extends Cancellable {

    override def cancel() = {
      remove(this)
    }
  }

  private var listeners: Set[Listener] = Set.empty

  def add(handler: Handler)(implicit executor: ExecutionContext): Cancellable = synchronized {
    val listener = Listener(handler, executor)
    listeners += listener
    listener
  }

  private def remove(listener: Listener): Unit = synchronized {
    listeners -= listener
  }

  /** Invoke the listener from a handler calling function.
    *
    * This function invokes all the listeners using their respective executors. To do so, it
    * uses the function passed as argument to call the handler as appropriate.
    *
    * @param f A function that invokes the handler with the appropriate parameters.
    */
  def invoke(f: Handler => Unit): Unit = synchronized {
    listeners.foreach(l =>
      l.executor.execute(new Runnable {
        override def run() = try {
          f(l.handler)
        } catch {
          case NonFatal(e) => logger.warn(
            "Fail to execute handler for property listener: unexpected exception thrown", e)
        }
      })
    )
  }
}
