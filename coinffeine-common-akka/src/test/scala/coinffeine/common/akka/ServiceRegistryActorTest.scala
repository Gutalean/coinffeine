package coinffeine.common.akka

import scala.concurrent.duration._

import akka.testkit.TestProbe

import coinffeine.common.akka.ServiceRegistryActor.ServiceId
import coinffeine.common.akka.test.AkkaSpec

class ServiceRegistryActorTest extends AkkaSpec {

  import ServiceRegistryActor._

  "Service registry" should "register and locate services" in new Fixture {
    registry ! RegisterService(serviceId, service.ref)
    registry ! LocateService(serviceId)
    expectMsg(ServiceLocated(serviceId, service.ref))
  }

  it should "fail to locate unknown service" in new Fixture {
    registry ! LocateService(serviceId)
    expectMsg(ServiceNotFound(serviceId))
  }

  it should "overwrite service registration" in new Fixture {
    registry ! RegisterService(serviceId, service.ref)
    registry ! RegisterService(serviceId, otherService.ref)
    registry ! LocateService(serviceId)
    expectMsg(ServiceLocated(serviceId, otherService.ref))
  }

  it should "eventually locate a service" in new Fixture {
    registry ! EventuallyLocateService(serviceId, 500 millis)
    expectNoMsg(200 millis)
    registry ! RegisterService(serviceId, service.ref)
    expectMsg(ServiceLocated(serviceId, service.ref))
  }

  it should "eventually locate a service if already registered" in new Fixture {
    registry ! RegisterService(serviceId, service.ref)
    registry ! EventuallyLocateService(serviceId, 500 millis)
    expectMsg(ServiceLocated(serviceId, service.ref))
  }

  it should "fail to eventually locate a service if none is registered" in new Fixture {
    registry ! EventuallyLocateService(serviceId, 500 millis)
    expectNoMsg(200 millis)
    expectMsg(ServiceNotFound(serviceId))
  }

  it should "not report located service after eventually location request timeouts" in new Fixture {
    registry ! EventuallyLocateService(serviceId, 500 millis)
    expectMsg(ServiceNotFound(serviceId))
    registry ! RegisterService(serviceId, service.ref)
    expectNoMsg()
  }

  it should "eventually locate a service with multiple requesters" in new Fixture {
    val requester1, requester2, requester3 = TestProbe()
    requester1.send(registry, EventuallyLocateService(serviceId, 500 millis))
    requester2.send(registry, EventuallyLocateService(serviceId, 500 millis))
    requester3.send(registry, EventuallyLocateService(serviceId, 100 millis))

    requester1.expectNoMsg(100 millis)
    requester2.expectNoMsg(100 millis)
    requester3.expectMsg(ServiceNotFound(serviceId))

    registry ! RegisterService(serviceId, service.ref)
    requester1.expectMsg(ServiceLocated(serviceId, service.ref))
    requester2.expectMsg(ServiceLocated(serviceId, service.ref))
    requester3.expectNoMsg()
  }

  trait Fixture {
    val registry = system.actorOf(ServiceRegistryActor.props())
    val service, otherService = TestProbe()
    val serviceId = ServiceId("serviceA")
  }
}
