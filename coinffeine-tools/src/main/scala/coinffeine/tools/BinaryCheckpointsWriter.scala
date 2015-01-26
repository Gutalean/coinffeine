package coinffeine.tools

import java.io.{DataOutputStream, OutputStream}
import java.nio.ByteBuffer
import java.security.{DigestOutputStream, MessageDigest}

import com.typesafe.scalalogging.StrictLogging
import org.bitcoinj.core.{Sha256Hash, StoredBlock}

class BinaryCheckpointsWriter extends CheckpointsWriter with StrictLogging {

  override def write(checkpoints: Seq[StoredBlock], outputStream: OutputStream): Unit = {
    new Writer(checkpoints, outputStream).write()
  }

  private class Writer(checkpoints: Seq[StoredBlock], outputStream: OutputStream) {
    private val buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE)
    private val digest = MessageDigest.getInstance("SHA-256")
    private val digestOutputStream = new DigestOutputStream(outputStream, digest)
    private val dataOutputStream = new DataOutputStream(digestOutputStream)

    def write(): Unit = {
      try {
        writeHeader()
        checkpoints.foreach(writeCheckpoint)
      } finally {
        dataOutputStream.close()
        digestOutputStream.close()
        outputStream.close()
      }
      logger.info("Hash of checkpoint data is {}", new Sha256Hash(digest.digest))
    }

    private def writeHeader(): Unit = {
      digestOutputStream.on(false)
      dataOutputStream.writeBytes("CHECKPOINTS 1")
      dataOutputStream.writeInt(0)
      digestOutputStream.on(true)
      dataOutputStream.writeInt(checkpoints.size)
    }

    private def writeCheckpoint(block: StoredBlock) = {
      block.serializeCompact(buffer)
      dataOutputStream.write(buffer.array)
      buffer.position(0)
    }
  }
}
