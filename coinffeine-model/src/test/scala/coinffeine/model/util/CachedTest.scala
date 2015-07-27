package coinffeine.model.util

import coinffeine.common.test.UnitTest

class CachedTest extends UnitTest {

  val freshGreet = Cached.fresh("hello")
  val staleGreet = Cached.stale("hello")

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
}
