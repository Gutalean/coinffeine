package coinffeine.protocol.messages.exchange

import java.math.BigInteger

import coinffeine.common.test.{EqualityBehaviors, UnitTest}
import coinffeine.model.Both
import coinffeine.model.bitcoin.TransactionSignature
import coinffeine.model.exchange.ExchangeId

class StepSignaturesTest extends UnitTest with EqualityBehaviors {

  val sig1 = new TransactionSignature(BigInteger.valueOf(0), BigInteger.valueOf(1))
  val sig2 = new TransactionSignature(BigInteger.valueOf(1), BigInteger.valueOf(0))

  "Step signatures" should behave like respectingEqualityProperties(equivalenceClasses = Seq(
    Seq(
      StepSignatures(ExchangeId("id"), 1, Both(buyer = sig1, seller = sig2)),
      StepSignatures(ExchangeId("id"), 1, Both(buyer = sig1, seller = sig2))
    ),
    Seq(
      StepSignatures(ExchangeId("id"), 1, Both(buyer = sig1, seller = sig1)),
      StepSignatures(ExchangeId("id"), 1, Both(buyer = sig1, seller = sig1))
    ),
    Seq(StepSignatures(ExchangeId("id2"), 1, Both(buyer = sig1, seller = sig2))),
    Seq(StepSignatures(ExchangeId("id"), 2, Both(buyer = sig1, seller = sig2)))
  ))
}
