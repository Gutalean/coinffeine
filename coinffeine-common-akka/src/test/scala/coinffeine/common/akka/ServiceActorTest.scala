package coinffeine.common.akka

import akka.actor.{Props, ActorRef, Actor}
import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec

class ServiceActorTest extends AkkaSpec {

  "Service actor" should "execute the starting process" in {
    val probe = TestProbe()
    val service = sampleService(probe)

    service ! Service.Start("Satoshi")
    probe.expectMsg("start")
    probe.send(service, "started")
    expectMsg(Service.Started)
  }

  it should "honour new behavior returned by start function" in {
    val probe = TestProbe()
    val service = startedSampleService("Satoshi", probe)

    service ! "Merkel"
    expectMsg("Hello Merkel, I'm Satoshi")
  }

  it should "fail to start an already started service" in {
    val probe = TestProbe()
    val service = startedSampleService("Satoshi", probe)

    service ! Service.Start("Merkel")
    expectMsgPF() { case Service.StartFailure(_) => }
  }

  it should "fail to start when passed arguments of the wrong type" in  {
    val probe = TestProbe()
    val service = sampleService(probe)

    service ! Service.Start(List(1, 2, 3))
    expectMsgPF() {
      case Service.StartFailure(cause: IllegalArgumentException) =>
        cause.getMessage should include ("Invalid start argument List(1, 2, 3)")
    }
  }

  it should "fail to start on start cancel" in {
    val probe = TestProbe()
    val service = sampleService(probe)

    service ! Service.Start("Satoshi")
    probe.expectMsg("start")
    probe.send(service, "start-failed")
    expectMsgPF() { case Service.StartFailure(_) => }
  }

  it should "invoke stop function on Stop message received" in {
    val probe = TestProbe()
    val service = startedSampleService("Satoshi", probe)

    service ! Service.Stop
    probe.expectMsg("stop")
    probe.send(service, "stopped")
    expectMsg(Service.Stopped)
  }

  it should "fail to stop a non started service" in {
    val probe = TestProbe()
    val service = sampleService(probe)

    service ! Service.Stop
    expectMsgPF() { case Service.StopFailure(_) => }
  }

  it should "honour become function" in {
    val probe = TestProbe()
    val service = startedSampleService("Satoshi", probe)

    service ! "Become"
    service ! "Merkel"
    expectMsg("Goodbye Merkel")
  }

  it should "call custom termination when requested in become started function" in {
    val probe = TestProbe()
    val service = sampleService(probe)
    service ! Service.Start("foo")
    probe.expectMsg("start")
    probe.send(service, "alternative-started")
    expectMsg(Service.Started)

    service ! Service.Stop
    probe.expectMsg("alternative-stop")
    probe.send(service, "stopped")
    expectMsg(Service.Stopped)
  }

  it should "call custom termination when requested in become function" in {
    val probe = TestProbe()
    val service = startedSampleService("Satoshi", probe)

    service ! "Become"
    service ! Service.Stop
    probe.expectMsg("alternative-stop")
    probe.send(service, "stopped")
    expectMsg(Service.Stopped)
  }

  it should "honor stopping behavior when not started" in {
    val probe = TestProbe()
    val service = sampleService(TestProbe())

    probe.send(service, "idle?")
    probe.expectMsg("true")

    service ! Service.Start("Foo")
    service ! "started"
    expectMsg(Service.Started)
    service ! Service.Stop
    service ! "stopped"
    expectMsg(Service.Stopped)

    probe.send(service, "idle?")
    probe.expectMsg("true")
  }

  private class SampleService(probe: ActorRef) extends Actor with ServiceActor[String] {

    protected override def starting(args: String): Receive = {
      probe ! "start"
      handle {
        case "started" => becomeStarted(sayingHello(args))
        case "alternative-started" => becomeStarted(sayingHello(args), alternativeStopping)
        case "start-failed" => cancelStart(new RuntimeException("Oh no! More lemmings!"))
      }
    }

    override protected def stopping() = {
      probe ! "stop"
      handle  {
        case "stopped" => becomeStopped()
      }
    }

    override protected def stopped: Receive = {
      case "idle?" => sender ! "true"
    }

    private def alternativeStopping: Receive = {
      probe ! "alternative-stop"
      handle  {
        case "stopped" => becomeStopped()
      }
    }

    def sayingHello(me: String): Receive = {
      case "Become" => become(sayingGoodbye, alternativeStopping)
      case name: String => sender ! s"Hello $name, I'm $me"
    }

    def sayingGoodbye: Receive = {
      case name: String => sender ! s"Goodbye $name"
    }
  }

  private def sampleService(probe: TestProbe): ActorRef =
    system.actorOf(Props(new SampleService(probe.ref)))

  private def startedSampleService(name: String, probe: TestProbe): ActorRef = {
    val service = sampleService(probe)
    service ! Service.Start(name)
    probe.expectMsg("start")
    probe.send(service, "started")
    expectMsg(Service.Started)
    service
  }
}
