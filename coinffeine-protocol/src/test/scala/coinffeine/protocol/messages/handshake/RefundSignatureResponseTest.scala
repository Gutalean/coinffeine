package coinffeine.protocol.messages.handshake

import java.math.BigInteger

import coinffeine.common.test.{EqualityBehaviors, UnitTest}
import coinffeine.model.bitcoin.TransactionSignature
import coinffeine.model.exchange.ExchangeId

class RefundSignatureResponseTest extends UnitTest with EqualityBehaviors {
  val sig1 = new TransactionSignature(BigInteger.valueOf(0), BigInteger.valueOf(1))
  val sig2 = new TransactionSignature(BigInteger.valueOf(1), BigInteger.valueOf(0))

  "Step signatures" should behave like respectingEqualityProperties(equivalenceClasses = Seq(
    Seq(
      RefundSignatureResponse(ExchangeId("id"), sig1),
      RefundSignatureResponse(ExchangeId("id"), sig1)
    ),
    Seq(RefundSignatureResponse(ExchangeId("id2"), sig1)),
    Seq(RefundSignatureResponse(ExchangeId("id1"), sig2))
  ))

  it should "have a compact string representation" in {
    RefundSignatureResponse(ExchangeId.random(), sig1).toString should
      fullyMatch regex """RefundSignatureResponse\(exchange .*, Signature\(.*\)\)"""
  }
}
