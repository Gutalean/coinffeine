package coinffeine.peer.payment.okpay

import java.security.MessageDigest

import org.joda.time.{DateTime, DateTimeZone}

/** Generates crypto tokens based on current datetime */
class TokenGenerator(seedToken: String) {

  def build(currentTime: DateTime): String = {
    val referenceTime = currentTime.toDateTime(DateTimeZone.UTC)
    val date = referenceTime.toString("yyyyMMdd")
    val hour = referenceTime.toString("HH")
    val currentToken = String.format("%s:%s:%s", seedToken, date, hour)
    val hash = MessageDigest.getInstance("SHA-256").digest(currentToken.getBytes("UTF-8"))
    hash.map("%02X" format _).mkString
  }
}
