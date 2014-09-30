package coinffeine.peer.payment.okpay

import akka.actor.Props
import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.currency._
import coinffeine.model.payment.PaymentProcessor
import coinffeine.model.payment.PaymentProcessor.BlockedFundsId
import coinffeine.peer.payment.PaymentProcessorActor

class BlockingFundsActorTest extends AkkaSpec {

  it must "retrieve no blocked funds when no funds are blocked" in new WithBlockingFundsActor {
    actor ! BlockingFundsActor.RetrieveBlockedFunds(Euro)
    expectMsg(BlockingFundsActor.BlockedFunds(Euro.Zero))
  }

  it must "retrieve blocked funds when blocked" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    givenAvailableFunds(100.EUR) { (listener, funds) =>
      actor ! BlockingFundsActor.RetrieveBlockedFunds(Euro)
      expectMsg(BlockingFundsActor.BlockedFunds(100.EUR))
    }
  }

  it must "retrieve no blocked funds after unblocked" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    givenAvailableFunds(100.EUR) { (listener, funds) =>
      actor ! PaymentProcessorActor.UnblockFunds(funds)
      actor ! BlockingFundsActor.RetrieveBlockedFunds(Euro)
      expectMsg(BlockingFundsActor.BlockedFunds(Euro.Zero))
    }
  }

  it must "retrieve blocked after using some" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    givenAvailableFunds(100.EUR) { (listener, funds) =>
      listener.send(actor, BlockingFundsActor.UseFunds(funds, 60.EUR))
      listener.expectMsgPF() { case BlockingFundsActor.FundsUsed(`funds`, _) => }
      actor ! BlockingFundsActor.RetrieveBlockedFunds(Euro)
      expectMsg(BlockingFundsActor.BlockedFunds(40.EUR))
    }
  }

  it must "block funds up to existing balances" in new WithBlockingFundsActor {
    actor ! BlockingFundsActor.BalancesUpdate(Seq(100.EUR))
    actor ! PaymentProcessorActor.BlockFunds(50.EUR)
    actor ! PaymentProcessorActor.BlockFunds(50.EUR)
    actor ! PaymentProcessorActor.BlockFunds(50.EUR)
    expectMsgAllConformingOf(
      classOf[PaymentProcessor.BlockedFundsId],
      classOf[PaymentProcessorActor.AvailableFunds],
      classOf[PaymentProcessor.BlockedFundsId],
      classOf[PaymentProcessorActor.AvailableFunds],
      classOf[PaymentProcessor.BlockedFundsId],
      classOf[PaymentProcessorActor.UnavailableFunds]
    )
  }

  it must "unblock blocked funds to make then available again" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    givenAvailableFunds(100.EUR) { (listener, funds) =>
      actor ! PaymentProcessorActor.UnblockFunds(funds)
    }
    givenBlockedFunds(100.EUR) { (listener, funds) =>
      listener.expectMsg(PaymentProcessorActor.AvailableFunds(funds))
    }
  }

  it must "notify unavailable funds once block submission when insufficient funds" in
    new WithBlockingFundsActor {
      setBalance(100.EUR)
      givenBlockedFunds(110.EUR) { (listener, funds) =>
        listener.expectMsg(PaymentProcessorActor.UnavailableFunds(funds))
      }
    }

  it must "notify unavailable funds to the last ones when blocks exceeds the funds" in
    new WithBlockingFundsActor {
      setBalance(100.EUR)
      givenAvailableFunds(90.EUR) { (listener1, funds1) =>
        givenAvailableFunds(10.EUR) { (listener2, funds2) =>
          setBalance(90.EUR)
          listener2.expectMsg(PaymentProcessorActor.UnavailableFunds(funds2))

          setBalance(50.EUR)
          listener1.expectMsg(PaymentProcessorActor.UnavailableFunds(funds1))
        }
      }
    }

  it must "notify available funds when balance is enough again due to external increase" in
    new WithBlockingFundsActor {
      setBalance(100.EUR)
      givenAvailableFunds(100.EUR) { (listener, funds) =>
        setBalance(50.EUR)
        listener.expectMsg(PaymentProcessorActor.UnavailableFunds(funds))
        setBalance(100.EUR)
        listener.expectMsg(PaymentProcessorActor.AvailableFunds(funds))
      }
    }

  it must "notify available funds when balance is enough again due to unblocking" in
    new WithBlockingFundsActor {
      setBalance(100.EUR)
      givenAvailableFunds(50.EUR) { (listener1, funds1) =>
        givenAvailableFunds(50.EUR) { (listener2, funds2) =>
          setBalance(60.EUR)
          listener2.expectMsg(PaymentProcessorActor.UnavailableFunds(funds2))
          actor ! PaymentProcessorActor.UnblockFunds(funds1)
          listener2.expectMsg(PaymentProcessorActor.AvailableFunds(funds2))
        }
      }
    }

  it must "reject funds usage for unknown funds id" in new WithBlockingFundsActor {
    actor ! BlockingFundsActor.UseFunds(BlockedFundsId(100), 10.EUR)
    expectMsgPF() { case BlockingFundsActor.CannotUseFunds(BlockedFundsId(100), _, _) => }
  }

  it must "reject funds usage when it exceeds blocked amount" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    givenAvailableFunds(50.EUR) { (listener, funds) =>
      listener.send(actor, BlockingFundsActor.UseFunds(funds, 100.EUR))
      listener.expectMsgPF() { case BlockingFundsActor.CannotUseFunds(`funds`, _, _) => }
    }
  }

  it must "reject funds usage when block is unavailable" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    givenUnavailableFunds(150.EUR) { (listener, funds) =>
      listener.send(actor, BlockingFundsActor.UseFunds(funds, 100.EUR))
      listener.expectMsgPF() { case BlockingFundsActor.CannotUseFunds(`funds`, _, _) => }
    }
  }

  it must "accept funds usage when amount is less than blocked funds" in
    new WithBlockingFundsActor {
      setBalance(100.EUR)
      givenAvailableFunds(50.EUR) { (listener, funds) =>
        listener.send(actor, BlockingFundsActor.UseFunds(funds, 10.EUR))
        listener.expectMsgPF() { case BlockingFundsActor.FundsUsed(`funds`, _) => }
      }
    }

  it must "accept funds usage when amount is equals to blocked funds" in
    new WithBlockingFundsActor {
      setBalance(100.EUR)
      givenAvailableFunds(50.EUR) { (listener, funds) =>
        listener.send(actor, BlockingFundsActor.UseFunds(funds, 50.EUR))
        listener.expectMsgPF() { case BlockingFundsActor.FundsUsed(`funds`, _) => }
      }
    }

  it must "consider new balance after funds are used" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    givenAvailableFunds(50.EUR) { (listener1, funds1) =>
      listener1.send(actor, BlockingFundsActor.UseFunds(funds1, 10.EUR))
      listener1.expectMsgPF() { case BlockingFundsActor.FundsUsed(`funds1`, _) => }
      givenBlockedFunds(60.EUR) { (listener2, funds2) =>
        listener2.expectMsg(PaymentProcessorActor.UnavailableFunds(funds2))
      }
    }
  }

  private trait WithBlockingFundsActor {
    val actor = system.actorOf(Props(new BlockingFundsActor()))

    def givenBlockedFunds(amount: FiatAmount)(block: (TestProbe, BlockedFundsId) => Unit): Unit = {
      val listener = TestProbe()
      listener.send(actor, PaymentProcessorActor.BlockFunds(amount))
      val funds = listener.expectMsgClass(classOf[PaymentProcessor.BlockedFundsId])
      block(listener, funds)
    }

    def givenAvailableFunds(amount: FiatAmount)
                           (block: (TestProbe, BlockedFundsId) => Unit): Unit = {
      givenBlockedFunds(amount) { (listener, funds) =>
        listener.expectMsg(PaymentProcessorActor.AvailableFunds(funds))
        block(listener, funds)
      }
    }

    def givenUnavailableFunds(amount: FiatAmount)
                             (block: (TestProbe, BlockedFundsId) => Unit): Unit = {
      givenBlockedFunds(amount) { (listener, funds) =>
        listener.expectMsg(PaymentProcessorActor.UnavailableFunds(funds))
        block(listener, funds)
      }
    }

    def setBalance(balance: FiatAmount): Unit = {
      actor ! BlockingFundsActor.BalancesUpdate(Seq(balance))
    }
  }
}
