package coinffeine.common.test

import akka.actor.ActorSystem
import akka.testkit.TestKitExtension
import org.scalatest._

/** Default trait for unit tests with load-pattern fixtures that mixes the most used testing traits. */
trait FixtureUnitTest extends fixture.FlatSpecLike
    with ShouldMatchers with BeforeAndAfter with FutureMatchers {

  override def scaleFactor: Double = TestKitExtension.get(ActorSystem()).TestTimeFactor
}
