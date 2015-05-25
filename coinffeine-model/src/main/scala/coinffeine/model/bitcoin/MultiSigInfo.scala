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

  /** Extract keys from script chunks
    *
    * First chunk is number of required keys and therefore discarded.
    * Last two chunks are number of keys and OP_CHECKMULTISIG, also discarded.
    */
  private def decodeKeys(chunks: Seq[ScriptChunk]): Seq[PublicKey] = chunks
    .slice(1, chunks.size - 2)
    .map(chunk => PublicKey(chunk.data))
}
