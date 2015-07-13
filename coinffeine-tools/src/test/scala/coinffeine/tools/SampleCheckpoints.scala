package coinffeine.tools

import org.bitcoinj.core.{Block, Sha256Hash, StoredBlock}

import coinffeine.model.bitcoin._
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork

object SampleCheckpoints {

  val checkpoints = {
    val version = 1
    val date = 1422274125855L
    val difficultyTarget = 1234567L
    val nonce = 42
    val height = 12355
    val chainWork: BigInt = 12

    Seq(
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
        height
      )
    )
  }

  private def hash(contents: String): Hash = {
    Sha256Hash.create(contents.getBytes)
  }
}
