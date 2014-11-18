package coinffeine.model.bitcoin

object MultiSigOutput {
  def unapply(output: MutableTransactionOutput): Option[MultiSigInfo] =
    MultiSigInfo.fromScript(output.getScriptPubKey)
}
