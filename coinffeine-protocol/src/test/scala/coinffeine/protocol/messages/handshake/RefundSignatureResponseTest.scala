package coinffeine.protocol.messages.handshake

import java.math.BigInteger

import coinffeine.model.bitcoin.TransactionSignature
import coinffeine.model.exchange.Exchange
import com.coinffeine.common.test.{EqualityBehaviors, UnitTest}

class RefundSignatureResponseTest extends UnitTest with EqualityBehaviors {
  val sig1 = new TransactionSignature(BigInteger.valueOf(0), BigInteger.valueOf(1))
  val sig2 = new TransactionSignature(BigInteger.valueOf(1), BigInteger.valueOf(0))

  "Step signatures" should behave like respectingEqualityProperties(equivalenceClasses = Seq(
    Seq(
      RefundSignatureResponse(Exchange.Id("id"), sig1),
      RefundSignatureResponse(Exchange.Id("id"), sig1)
    ),
    Seq(RefundSignatureResponse(Exchange.Id("id2"), sig1)),
    Seq(RefundSignatureResponse(Exchange.Id("id1"), sig2))
  ))
}
