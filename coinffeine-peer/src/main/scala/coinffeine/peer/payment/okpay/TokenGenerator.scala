package coinffeine.peer.payment.okpay

import org.joda.time.DateTime

/** Generates crypto tokens based on current datetime */
private[okpay] trait TokenGenerator {

  def build(currentTime: DateTime): String
}

object TokenGenerator {

  trait Component {
    def createTokenGenerator(seedToken: String): TokenGenerator
  }
}
