package coinffeine.model.exchange

import coinffeine.model.bitcoin.PublicKey
import coinffeine.model.currency.FiatCurrency

/** Relevant information for an ongoing exchange. This point of view is only held by the parts
  * as contains information not made public to everyone on the network.
  */
trait OngoingExchange[+C <: FiatCurrency] extends Exchange[C] {
  /** Information about the parts */
  val participants: Both[Exchange.PeerInfo]

  def requiredSignatures: Both[PublicKey] = participants.map(_.bitcoinKey)

  val user = role.select(participants)
  val counterpart = role.counterpart.select(participants)

  require(user.bitcoinKey.hasPrivKey)
}
