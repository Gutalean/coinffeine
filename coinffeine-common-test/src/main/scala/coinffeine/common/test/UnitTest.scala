package coinffeine.common.test

import akka.actor.ActorSystem
import akka.testkit.TestKitExtension
import org.scalatest.{BeforeAndAfter, FlatSpecLike, ShouldMatchers}

/** Default trait for unit tests that mixes the most typical testing traits. */
trait UnitTest extends FlatSpecLike with ShouldMatchers with BeforeAndAfter with FutureMatchers {
  override def scaleFactor: Double = TestKitExtension.get(ActorSystem()).TestTimeFactor
}
