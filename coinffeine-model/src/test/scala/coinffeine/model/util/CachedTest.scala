package coinffeine.model.util

import coinffeine.common.test.UnitTest

class CachedTest extends UnitTest {

  val freshGreet = Cached.fresh("hello")
  val staleGreet = Cached.stale("hello")
  val freshName = Cached.fresh("Joe")
  val staleName = Cached.stale("Joe")

  "A cached value" should "have a fresh attribute" in {
    freshGreet shouldBe 'fresh
    staleGreet should not be 'fresh
  }

  it should "be staled" in {
    freshGreet.staled shouldBe staleGreet
    staleGreet.staled shouldBe staleGreet
  }

  it should "be mapped" in {
    freshGreet.map(_.length) shouldBe Cached.fresh(5)
    staleGreet.map(_.length) shouldBe Cached.stale(5)
  }

  it should "be flatMapped to a fresh result if all inputs are fresh" in {
    composeGreeting(freshGreet, freshName) shouldBe Cached.fresh("hello Joe!")
  }

  it should "be flatMapped to a stale result if some input is stale" in {
    composeGreeting(staleGreet, freshName) shouldBe Cached.stale("hello Joe!")
    composeGreeting(freshGreet, staleName) shouldBe Cached.stale("hello Joe!")
  }

  private def composeGreeting(greet: Cached[String], name: Cached[String]) = for {
    greet <- greet
    name <- name
  } yield s"$greet $name!"
}
