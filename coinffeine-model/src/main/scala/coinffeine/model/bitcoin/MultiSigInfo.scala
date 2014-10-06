package coinffeine.model.bitcoin

import scala.collection.JavaConversions._

import org.bitcoinj.script.{Script, ScriptChunk}

case class MultiSigInfo(possibleKeys: Seq[PublicKey], requiredKeyCount: Int)

object MultiSigInfo {

  /** Tries to extract multisig information from an script */
  def fromScript(script: Script): Option[MultiSigInfo] =
    for (chunks <- requireMultisigChunks(script))
    yield MultiSigInfo(decodeKeys(chunks), script.getNumberOfSignaturesRequiredToSpend)

  private def requireMultisigChunks(script: Script): Option[Seq[ScriptChunk]] =
    if (script.isSentToMultiSig) Some(script.getChunks) else None

  private def decodeKeys(chunks: Seq[ScriptChunk]): Seq[PublicKey] = chunks
    .take(chunks.size - 2) // Last two chunks are number of keys and OP_CHECKMULTISIG
    .drop(1) // First chunk is number of required keys
    .map(chunk => PublicKey(chunk.data))
}
