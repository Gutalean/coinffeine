package coinffeine.common.akka

import scala.concurrent.duration._
import scala.util.control.NoStackTrace

import akka.testkit._
import akka.util.Timeout

import coinffeine.common.akka.Service.ParallelServiceStopFailure
import coinffeine.common.akka.test.AkkaSpec
import coinffeine.common.test.FutureMatchers

class ServiceTest extends AkkaSpec with FutureMatchers {

  implicit val timeout = Timeout(500.millis.dilated)
  import system.dispatcher
  case object ServiceArgs
  val cause = new Exception("injected error") with NoStackTrace

  "A service helper" should "be able to successfully start a service" in {
    val probe = TestProbe()
    val result = Service.askStart(probe.ref, ServiceArgs)
    probe.expectMsg(Service.Start(ServiceArgs))
    probe.reply(Service.Started)
    result.futureValue shouldBe {}
  }

  it should "report starting timeouts" in {
    val probe = TestProbe()
    val result = Service.askStart(probe.ref)
    val ex = the [Exception] thrownBy {
      result.futureValue
    }
    ex.getMessage should include regex "timeout of .* waiting for response"
  }

  it should "report starting failures" in {
    val probe = TestProbe()
    val result = Service.askStart(probe.ref)
    probe.expectMsg(Service.Start {})
    probe.reply(Service.StartFailure(cause))
    val ex = the [Exception] thrownBy {
      result.futureValue
    }
    ex.getCause shouldBe cause
  }

  it should "be able to successfully stop a service" in {
    val probe = TestProbe()
    val result = Service.askStop(probe.ref)
    probe.expectMsg(Service.Stop)
    probe.reply(Service.Stopped)
    result.futureValue shouldBe {}
  }

  it should "report stopping timeouts" in {
    val probe = TestProbe()
    val result = Service.askStop(probe.ref)
    val ex = the [Exception] thrownBy {
      result.futureValue
    }
    ex.getMessage should include regex "timeout of .* waiting for response"
  }

  it should "report stopping failures" in {
    val probe = TestProbe()
    val result = Service.askStop(probe.ref)
    probe.expectMsg(Service.Stop)
    probe.reply(Service.StopFailure(cause))
    val ex = the [Exception] thrownBy {
      result.futureValue
    }
    ex.getCause shouldBe cause
  }

  it should "stop several services in parallel" in {
    val probe1, probe2 = TestProbe()
    val result = Service.askStopAll(probe1.ref, probe2.ref)

    probe1.expectMsg(Service.Stop)
    probe1.reply(Service.Stopped)
    probe2.expectMsg(Service.Stop)
    probe2.reply(Service.Stopped)

    result.futureValue shouldBe {}
  }

  it should "collect all failure errors when stopping services in parallel" in {
    val probe1, probe2 = TestProbe()
    val result = Service.askStopAll(probe1.ref, probe2.ref)

    probe1.expectMsg(Service.Stop)
    probe1.reply(Service.StopFailure(cause))
    probe2.expectMsg(Service.Stop)

    val failure = the [ParallelServiceStopFailure] thrownBy {
      result.futureValue
    }
    failure.stopFailures(probe1.ref).getCause shouldBe cause
    failure.stopFailures(probe2.ref).getMessage should include ("timeout")
  }
}
