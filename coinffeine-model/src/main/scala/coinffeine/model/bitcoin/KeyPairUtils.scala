package coinffeine.model.bitcoin

object KeyPairUtils {

  /** The implementation of equals is broken as it considers key creations instant. hashCode is
    * nevertheless OK.
    */
  def equals(k1: KeyPair, k2: KeyPair): Boolean = (k1, k2) match {
    case (null, null) => true
    case (_, null) | (null, _) => false
    case _ => (k1.canSign == k2.canSign) && (!k1.canSign || (k1.getPrivKey == k2.getPrivKey))
  }
}
