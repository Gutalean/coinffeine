package coinffeine.common.akka

import akka.actor.{Props, ActorRef, Actor}
import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec

class ServiceActorTest extends AkkaSpec {

  "Service actor" should "execute the starting process" in {
    val probe = TestProbe()
    val service = sampleService(probe)

    service ! ServiceActor.Start("Satoshi")
    probe.expectMsg("start")
    probe.send(service, "started")
    expectMsg(ServiceActor.Started)
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

    service ! ServiceActor.Start("Merkel")
    expectMsgPF() { case ServiceActor.StartFailure(_) => }
  }

  it should "fail to start when passed arguments of the wrong type" in  {
    val probe = TestProbe()
    val service = sampleService(probe)

    service ! ServiceActor.Start(List(1, 2, 3))
    expectMsgPF() {
      case ServiceActor.StartFailure(cause: IllegalArgumentException) =>
        cause.getMessage should include ("Invalid start argument List(1, 2, 3)")
    }
  }

  it should "fail to start on start cancel" in {
    val probe = TestProbe()
    val service = sampleService(probe)

    service ! ServiceActor.Start("Satoshi")
    probe.expectMsg("start")
    probe.send(service, "start-failed")
    expectMsgPF() { case ServiceActor.StartFailure(_) => }
  }

  it should "invoke stop function on Stop message received" in {
    val probe = TestProbe()
    val service = startedSampleService("Satoshi", probe)

    service ! ServiceActor.Stop
    probe.expectMsg("stop")
    probe.send(service, "stopped")
    expectMsg(ServiceActor.Stopped)
  }

  it should "fail to stop a non started service" in {
    val probe = TestProbe()
    val service = sampleService(probe)

    service ! ServiceActor.Stop
    expectMsgPF() { case ServiceActor.StopFailure(_) => }
  }

  it should "honour become function" in {
    val probe = TestProbe()
    val service = startedSampleService("Satoshi", probe)

    service ! "Become"
    service ! "Merkel"
    expectMsg("Goodbye Merkel")
  }

  it should "call custom termination when requested in become function" in {
    val probe = TestProbe()
    val service = startedSampleService("Satoshi", probe)

    service ! "Become"
    service ! ServiceActor.Stop
    probe.expectMsg("alternative-stop")
    probe.send(service, "stopped")
    expectMsg(ServiceActor.Stopped)
  }

  it should "honor stopping behavior when not started" in {
    val probe = TestProbe()
    val service = sampleService(TestProbe())

    probe.send(service, "idle?")
    probe.expectMsg("true")

    service ! ServiceActor.Start("Foo")
    service ! "started"
    expectMsg(ServiceActor.Started)
    service ! ServiceActor.Stop
    service ! "stopped"
    expectMsg(ServiceActor.Stopped)

    probe.send(service, "idle?")
    probe.expectMsg("true")
  }

  private class SampleService(probe: ActorRef) extends Actor with ServiceActor[String] {

    protected override def starting(args: String): Receive = {
      probe ! "start"
      handle {
        case "started" => becomeStarted(sayingHello(args))
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
    service ! ServiceActor.Start(name)
    probe.expectMsg("start")
    probe.send(service, "started")
    expectMsg(ServiceActor.Started)
    service
  }
}
