package coinffeine.benchmark.action

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

import akka.actor.ActorRef
import io.gatling.core.action.Action
import io.gatling.core.result.message.{KO, OK, Status}
import io.gatling.core.result.writer.{RequestMessage, DataWriter}
import io.gatling.core.session._

trait ReportingSupport { self: Action =>

  def next: ActorRef
  def requestName: Expression[String]

  def execute(session: Session): Unit = {
    val start = System.currentTimeMillis()
      val result = try { runAction() } catch { case NonFatal(e) => Future.failed(e) }
      result.onComplete {
        case Success(_) => finalize(session, start, OK, None)
        case Failure(ex) => finalize(session, start, KO, Some(s"unexpected exception: $ex"))
      }
  }

  def runAction(): Future[_]

  private def finalize(session: Session, start: Long,
                       status: Status = OK, message: Option[String] = None): Unit = {
    val end = System.currentTimeMillis()
    DataWriter.instances.foreach(_ ! RequestMessage(
      scenario = session.scenarioName,
      userId = session.userId,
      groupHierarchy = session.groupHierarchy,
      name = requestName(session).get,
      requestStartDate = start,
      requestEndDate = start,
      responseStartDate = end,
      responseEndDate = end,
      status,
      message,
      extraInfo = Nil))
    next ! session
  }
}
