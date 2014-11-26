package coinffeine.headless.parsing

import coinffeine.common.test.UnitTest

class TokenizerTest extends UnitTest {

  "A tokenizer" should "split in words the empty string" in {
    Tokenizer.splitWords("") shouldBe Array.empty
  }

  it should "split whitespace delimited tokens" in {
    Tokenizer.splitWords("En un lugar de la Mancha") shouldBe
      Array("En", "un", "lugar", "de", "la", "Mancha")
  }

  it should "ignore extra spaces" in {
    Tokenizer.splitWords("  En\tun   lugar \tde\t la Mancha  ") shouldBe
      Array("En", "un", "lugar", "de", "la", "Mancha")
  }
}
