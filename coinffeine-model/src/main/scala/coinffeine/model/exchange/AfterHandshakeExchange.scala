package coinffeine.model.exchange

import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.currency.FiatCurrency

trait AfterHandshakeExchange[C <: FiatCurrency] extends Exchange[C] {

  override def isStarted = true

  val user: Exchange.PeerInfo
  val counterpart: Exchange.PeerInfo

  def participants: Both[Exchange.PeerInfo] = Both.fromSeq(metadata.role match {
    case BuyerRole => Seq(user, counterpart)
    case SellerRole => Seq(counterpart, user)
  })

  def requiredSignatures: Both[PublicKey] = participants.map(_.bitcoinKey)
}
