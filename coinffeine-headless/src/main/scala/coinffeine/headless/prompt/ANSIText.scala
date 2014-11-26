package coinffeine.headless.prompt

/** Utility for formatting text using ANSI escape sequences */
object ANSIText {

  class EscapedText(code: Int) {
    def apply(text: String*): String = text.map(wrap).mkString("")

    private def wrap(fragment: String): String = escape(code) + fragment + escape(0)

    private def escape(code: Int) = s"\u001b[${code}m"
  }

  object Bold extends EscapedText(1)

  object Black extends EscapedText(30)
  object Red extends EscapedText(31)
  object Green extends EscapedText(32)
  object Yellow extends EscapedText(33)
  object Blue extends EscapedText(34)
  object Magenta extends EscapedText(35)
  object Cyan extends EscapedText(36)
  object White extends EscapedText(37)
}
