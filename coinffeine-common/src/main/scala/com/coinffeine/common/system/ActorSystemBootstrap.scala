package com.coinffeine.common.system

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

import akka.Main
import akka.actor.{ActorSystem, Props}
import akka.pattern._
import akka.util.Timeout

/** Bootstrap of an actor system whose supervisor actor is configured from CLI arguments and
  * whose termination stops the application.
  */
trait ActorSystemBootstrap {
  import ActorSystemBootstrap._

  protected val supervisorProps: Props

  private val system = ActorSystem("Main")

  def main(commandLine: Array[String]): Unit = {
    Await.result(startSupervisor(commandLine), StartupTimeout) match {
      case StartFailure(cause) => system.log.error("Cannot start the system", cause)
      case Started => system.log.info("System started")
    }
  }

  private def startSupervisor(commandLine: Array[String]): Future[StartResult] = try {
    val supervisor = system.actorOf(supervisorProps, "supervisor")
    system.actorOf(Props(classOf[Main.Terminator], supervisor), "terminator")
    supervisor.ask(Start(commandLine))(Timeout(StartupTimeout)).mapTo[StartResult]
  } catch {
    case NonFatal(ex) =>
      system.shutdown()
      throw ex
  }
}

object ActorSystemBootstrap {
  val StartupTimeout = 30.seconds

  case class Start(commandLine: Array[String])
  sealed trait StartResult
  case object Started extends StartResult
  case class StartFailure(cause: Throwable) extends StartResult
}
