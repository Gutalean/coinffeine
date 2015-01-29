package coinffeine.common.akka

import akka.actor.{Actor, Props}

import coinffeine.common.akka.test.AkkaSpec

class ServiceLifecycleTest extends AkkaSpec {

  case class RenameTo(name: String)

  class TestService extends Actor with ServiceLifecycle[String] {

    override protected def onStart(name: String): StartTransition = {
      name match {
        case "fail-immediately" => CancelStart(new Exception(name))
        case "intermediate-state" => BecomeStarting {
          case "probe" => sender() ! "starting"
          case "fail" => cancelStart(new Error(name))
          case delayedName: String => completeStart(named(delayedName))
        }
        case _ => BecomeStarted(named(name))
      }
    }

    override protected def stopped = {
      case "probe" => sender() ! "stopped"
    }

    private def named(name: String): Receive = {
      case "probe" => sender() ! s"started as $name"
      case "intermediate-state" => become(named(name), twoStepsStop)
      case RenameTo(newName) => become(named(newName))
    }

    private def twoStepsStop(): StopTransition = {
      BecomeStopping {
        case "probe" => sender() ! "stopping"
        case "complete-stop" => completeStop()
      }
    }
  }

  "A service actor" should "reject stopping when stopped" in new FreshService {
    service ! Service.Stop
    expectMsgType[Service.StopFailure]
  }

  it should "reject starting when started" in new FreshService {
    service ! Service.Start("Piotr")
    expectMsg(Service.Started)
    service ! Service.Start("Wozniak")
    expectMsgType[Service.StartFailure]
  }

  it should "have a behavior when stopped and other when started" in new FreshService {
    expectState("stopped")
    startAs("Ebbinghaus")
    service ! Service.Stop
    expectMsg(Service.Stopped)
    expectState("stopped")
  }

  it should "support immediate start failures" in new FreshService {
    service ! Service.Start("fail-immediately")
    expectMsgType[Service.StartFailure]
  }

  it should "support behavior changes on the go" in new FreshService {
    startAs("Samaras")
    service ! RenameTo("Tsipras")
    expectState("started as Tsipras")
  }

  it should "support intermediate starting state" in new FreshService {
    service ! Service.Start("intermediate-state")
    expectState("starting")
    service ! "Merkel"
    expectMsg(Service.Started)
    expectState("started as Merkel")
  }

  it should "support delayed start failures" in new FreshService {
    service ! Service.Start("intermediate-state")
    service ! "fail"
    expectMsgType[Service.StartFailure]
  }

  it should "support intermediate stopping state" in new FreshService {
    startAs("foo")
    service ! "intermediate-state"
    service ! Service.Stop
    expectState("stopping")
    service ! "complete-stop"
    expectMsg(Service.Stopped)
  }

  trait FreshService {
    val service = system.actorOf(Props(new TestService))

    def expectState(state: String): Unit = {
      service ! "probe"
      expectMsg(state)
    }

    def startAs(name: String): Unit = {
      service ! Service.Start(name)
      expectMsg(Service.Started)
      expectState(s"started as $name")
    }
  }
}
