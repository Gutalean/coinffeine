package coinffeine.headless.prompt

import coinffeine.common.test.UnitTest
import coinffeine.headless.prompt.ANSIText._

class ANSITextTest extends UnitTest {

  "Text with ANSI escapes" should "support bold text" in {
    Bold("") shouldBe "\u001b[1m\u001b[0m"
    Bold("some text") shouldBe "\u001b[1msome text\u001b[0m"
  }

  it should "support colored text" in {
    Green("text") shouldBe "\u001b[32mtext\u001b[0m"
  }

  it should "support complex formatting" in {
    Bold(Green("[OK]"), Red("[Error]"), "> ") shouldBe
      "\u001b[1m\u001b[32m[OK]\u001b[0m\u001b[0m\u001b[1m\u001b[31m[Error]\u001b[0m\u001b[0m\u001b[1m> \u001b[0m"
  }
}
