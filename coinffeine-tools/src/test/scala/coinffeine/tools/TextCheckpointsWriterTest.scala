package coinffeine.tools

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

import org.bitcoinj.core.{Sha256Hash, Block, StoredBlock}

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.{MutableTransaction, Hash}
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork

class TextCheckpointsWriterTest extends UnitTest {

  val version = 1
  val date = 1422274125855L
  val difficultyTarget = 1234567L
  val nonce = 42
  val height = 12355
  val chainWork: BigInt = 12

  "Text checkpoints writer" should "write a header and then base64-encoded checkpoints" in {
    val checkpoints = Seq(
      new StoredBlock(
        new Block(
          CoinffeineUnitTestNetwork,
          version,
          hash("prev_block"),
          hash("merkle_root"),
          date,
          difficultyTarget,
          nonce,
          java.util.Collections.emptyList[MutableTransaction]()
        ),
        chainWork.underlying(),
        height)
    )
    val outputStream = new ByteArrayOutputStream()
    new TextCheckpointsWriter().write(checkpoints, outputStream)
    new String(outputStream.toByteArray, Charset.forName("US-ASCII")).trim shouldBe
      """
        |TXT CHECKPOINTS 1
        |0
        |1
        |AAAAAAAAAAAAAAAMAAAwQwEAAAC+mj84i2ltgBLSD1JNz5PWTV+34WWiEd8bOAwyVC7pifbmVjvPda8nSnnj/MYIMLHFpBmhTn1uOz9ZnHKzdDEWH+AkJofWEgAqAAAA
      """.stripMargin.trim
  }

  def hash(contents: String): Hash = {
    Sha256Hash.create(contents.getBytes)
  }
}
