package coinffeine.tools

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

import coinffeine.common.test.UnitTest

class TextCheckpointsWriterTest extends UnitTest {

  "Text checkpoints writer" should "write a header and then base64-encoded checkpoints" in {
    val outputStream = new ByteArrayOutputStream()
    new TextCheckpointsWriter().write(SampleCheckpoints.checkpoints, outputStream)
    new String(outputStream.toByteArray, Charset.forName("US-ASCII")).trim shouldBe
      """
        |TXT CHECKPOINTS 1
        |0
        |1
        |AAAAAAAAAAAAAAAMAAAwQwEAAAC+mj84i2ltgBLSD1JNz5PWTV+34WWiEd8bOAwyVC7pifbmVjvPda8nSnnj/MYIMLHFpBmhTn1uOz9ZnHKzdDEWH+AkJofWEgAqAAAA
      """.stripMargin.trim
  }
}
