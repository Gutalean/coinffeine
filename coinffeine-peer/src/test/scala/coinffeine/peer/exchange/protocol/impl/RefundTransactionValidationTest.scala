package coinffeine.peer.exchange.protocol.impl

import scalaz.{Failure, NonEmptyList}

import org.scalatest.Inside

import coinffeine.model.bitcoin._
import coinffeine.model.currency._
import coinffeine.peer.exchange.protocol.impl.RefundTransactionValidation._

class RefundTransactionValidationTest extends ExchangeTest with Inside {


  "A refund transaction validator" should "reject transactions with a different lock time" in
    new Fixture {
      val depositWithWrongLockTime = ImmutableTransaction {
        val tx = validTransaction.get
        tx.setLockTime(42)
        tx
      }
      inside(validator.apply(depositWithWrongLockTime)) {
        case Failure(NonEmptyList(InvalidLockTime(Some(42), _))) =>
      }
    }

  it should "reject transactions with a no lock time" in new Fixture {
    val depositWithWrongLockTime = ImmutableTransaction {
      val tx = validTransaction.get
      tx.setLockTime(0)
      tx
    }
    inside(validator.apply(depositWithWrongLockTime)) {
      case Failure(NonEmptyList(InvalidLockTime(None, _))) =>
    }
  }

  it should "reject transactions with other than one input" in new Fixture {
    val depositWithoutInputs = ImmutableTransaction {
      val tx = validTransaction.get
      tx.clearInputs()
      tx
    }
    inside(validator.apply(depositWithoutInputs)) {
      case Failure(NonEmptyList(InvalidInputs(_), _)) =>
    }
  }

  it should "reject transactions refunding an invalid refunded amount" in new Fixture {
    val depositWithWrongAmount = ImmutableTransaction {
      val tx = validTransaction.get
      tx.addOutput(1.BTC, new PublicKey)
      tx
    }
    inside(validator.apply(depositWithWrongAmount)) {
      case Failure(NonEmptyList(InvalidRefundedAmount(_, _))) =>
    }
  }

  it should "detect multiple problems" in new Fixture {
    val veryWrongTransaction = ImmutableTransaction {
      val tx = validTransaction.get
      tx.clearInputs()
      tx.setLockTime(0)
      tx.addOutput(1.BTC, new PublicKey)
      tx
    }
    inside(validator.apply(veryWrongTransaction)) {
      case Failure(errors) => errors.size should be > 1
    }
  }

  private trait Fixture extends BuyerHandshake {
    val validTransaction = buyerHandshake.myUnsignedRefund
    val expectedAmount = amounts.refunds.buyer
    val validator = new RefundTransactionValidation(parameters, expectedAmount)
  }
}
