package coinffeine.common.test

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit, TestKitExtension}
import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

/** FlatSpec configured to test Akka actors. */
abstract class AkkaSpec(actorSystem: ActorSystem = ActorSystem("TestSystem"))
  extends TestKit(actorSystem) with FlatSpecLike with BeforeAndAfterAll with ShouldMatchers
  with ImplicitSender with ScalaFutures {

  override def spanScaleFactor: Double =
    TestKitExtension.get(system).TestTimeFactor

  def this(systemName: String) = this(ActorSystem(systemName))

  override protected def afterAll(): Unit = {
    system.shutdown()
  }
}

object AkkaSpec {

  /** Create an actor system with logging interception enabled.
    *
    * @param name  Name of the actor system
    *
    * See [[akka.testkit.EventFilter]] for more details.
    */
  def systemWithLoggingInterception(name: String): ActorSystem =
    ActorSystem(name, ConfigFactory.parseString(
      """
        |akka {
        |   loggers = ["akka.testkit.TestEventListener"]
        |   mode = "test"
        |   akka.test.filter-leeway = 10s
        |}
      """.stripMargin
    ).withFallback(ConfigFactory.load()))
}
