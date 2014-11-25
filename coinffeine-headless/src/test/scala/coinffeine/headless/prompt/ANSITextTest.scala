package coinffeine.headless.prompt

import coinffeine.common.test.UnitTest
import coinffeine.headless.prompt.ANSIText._

class ANSITextTest extends UnitTest {

  "Text with ANSI escapes" should "support bold text" in {
    Bold("") shouldBe "\033[1m\033[0m"
    Bold("some text") shouldBe "\033[1msome text\033[0m"
  }

  it should "support colored text" in {
    Green("text") shouldBe "\033[32mtext\033[0m"
  }

  it should "support complex formatting" in {
    Bold(Green("[OK]"), Red("[Error]"), "> ") shouldBe
      "\033[1m\033[32m[OK]\033[0m\033[0m\033[1m\033[31m[Error]\033[0m\033[0m\033[1m> \033[0m"
  }
}
