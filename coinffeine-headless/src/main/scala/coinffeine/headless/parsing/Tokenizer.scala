package coinffeine.headless.parsing

object Tokenizer {
  def splitWords(text: String): Array[String] =
    text.split("\\s+").filterNot(_.isEmpty)
}
