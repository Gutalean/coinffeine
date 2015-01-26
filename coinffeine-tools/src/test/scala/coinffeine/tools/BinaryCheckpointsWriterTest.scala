package coinffeine.tools

import java.io.ByteArrayOutputStream

import org.bitcoinj.core.StoredBlock

import coinffeine.common.test.UnitTest

class BinaryCheckpointsWriterTest extends UnitTest {

  "A binary checkpoints writer" should "create a binary file" in {
    val outputStream = new ByteArrayOutputStream()
    new BinaryCheckpointsWriter().write(SampleCheckpoints.checkpoints, outputStream)
    val output = outputStream.toByteArray

    val magicString = "CHECKPOINTS 1".getBytes
    output.take(magicString.size) shouldBe magicString

    val headerSize = magicString.size + 8
    output.size shouldBe (headerSize +
      SampleCheckpoints.checkpoints.size * StoredBlock.COMPACT_SERIALIZED_SIZE)
  }
}
