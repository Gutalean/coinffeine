package coinffeine.tools

import java.io.{IOException, OutputStream}

import org.bitcoinj.core.StoredBlock

trait CheckpointsWriter {
  @throws[IOException]
  def write(checkpoints: Seq[StoredBlock], outputStream: OutputStream): Unit
}
