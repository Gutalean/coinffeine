package coinffeine.tools

import java.io._
import java.nio.ByteBuffer

import com.google.common.base.Charsets
import org.bitcoinj.core.{CheckpointManager, StoredBlock}

class TextCheckpointsWriter extends CheckpointsWriter {

  private val buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE)

  @throws[IOException]
  override def write(checkpoints: Seq[StoredBlock], outputStream: OutputStream): Unit = {
    val writer = new PrintWriter(new OutputStreamWriter(outputStream, Charsets.US_ASCII))
    try {
      writeHeader(writer, checkpoints)
      writeCheckpoints(writer, checkpoints)
    } finally {
      writer.close()
    }
  }

  private def writeHeader(writer: PrintWriter, checkpoints: Seq[StoredBlock]): Unit = {
    writer.println("TXT CHECKPOINTS 1")
    writer.println("0")
    writer.println(checkpoints.size)
  }

  private def writeCheckpoints(writer: PrintWriter, checkpoints: Seq[StoredBlock]): Unit = {
    for (block <- checkpoints) {
      block.serializeCompact(buffer)
      writer.println(CheckpointManager.BASE64.encode(buffer.array))
      buffer.position(0)
    }
  }
}
