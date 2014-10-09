package coinffeine.peer.payment.okpay

import akka.actor.Props
import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId
import coinffeine.peer.payment.PaymentProcessorActor

class BlockingFundsActorTest extends AkkaSpec {

  "The blocking funds actor" must "retrieve no blocked funds when no funds are blocked" in
    new WithBlockingFundsActor {
      actor ! BlockingFundsActor.RetrieveTotalBlockedFunds(Euro)
      expectMsg(BlockingFundsActor.TotalBlockedFunds(Euro.Zero))
    }

  it must "retrieve blocked funds when blocked" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    givenAvailableFunds(100.EUR) { (listener, funds) =>
      actor ! BlockingFundsActor.RetrieveTotalBlockedFunds(Euro)
      expectMsg(BlockingFundsActor.TotalBlockedFunds(100.EUR))
    }
  }

  it must "retrieve no blocked funds after unblocked" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    givenAvailableFunds(100.EUR) { (listener, funds) =>
      actor ! PaymentProcessorActor.UnblockFunds(funds)
      actor ! BlockingFundsActor.RetrieveTotalBlockedFunds(Euro)
      expectMsg(BlockingFundsActor.TotalBlockedFunds(Euro.Zero))
    }
  }

  it must "retrieve blocked after using some" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    givenAvailableFunds(100.EUR) { (listener, funds) =>
      listener.send(actor, BlockingFundsActor.UseFunds(funds, 60.EUR))
      listener.expectMsgPF() { case BlockingFundsActor.FundsUsed(`funds`, _) => }
      actor ! BlockingFundsActor.RetrieveTotalBlockedFunds(Euro)
      expectMsg(BlockingFundsActor.TotalBlockedFunds(40.EUR))
    }
  }

  it must "block funds up to existing balances" in new WithBlockingFundsActor {
    actor ! BlockingFundsActor.BalancesUpdate(Seq(100.EUR))
    actor ! PaymentProcessorActor.BlockFunds(ExchangeId.random(), 50.EUR)
    actor ! PaymentProcessorActor.BlockFunds(ExchangeId.random(), 50.EUR)
    actor ! PaymentProcessorActor.BlockFunds(ExchangeId.random(), 50.EUR)
    expectMsgAllConformingOf(
      classOf[PaymentProcessorActor.BlockedFunds],
      classOf[PaymentProcessorActor.AvailableFunds],
      classOf[PaymentProcessorActor.BlockedFunds],
      classOf[PaymentProcessorActor.AvailableFunds],
      classOf[PaymentProcessorActor.BlockedFunds],
      classOf[PaymentProcessorActor.UnavailableFunds]
    )
  }

  it must "reject blocking funds twice for the same exchange" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    actor ! PaymentProcessorActor.BlockFunds(ExchangeId("ex"), 50.EUR)
    expectMsgAllConformingOf(
      classOf[PaymentProcessorActor.BlockedFunds],
      classOf[PaymentProcessorActor.AvailableFunds]
    )
    actor ! PaymentProcessorActor.BlockFunds(ExchangeId("ex"), 20.EUR)
    expectMsgType[PaymentProcessorActor.AlreadyBlockedFunds]
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
    val unknownFunds = ExchangeId("unknown")
    actor ! BlockingFundsActor.UseFunds(unknownFunds, 10.EUR)
    expectMsgPF() { case BlockingFundsActor.CannotUseFunds(`unknownFunds`, _, _) => }
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

    def givenBlockedFunds(amount: FiatAmount, fundsId: ExchangeId = ExchangeId.random())
                         (block: (TestProbe, ExchangeId) => Unit): Unit = {
      val listener = TestProbe()
      listener.send(actor, PaymentProcessorActor.BlockFunds(fundsId, amount))
      listener.expectMsgType[PaymentProcessorActor.BlockedFunds]
      block(listener, fundsId)
    }

    def givenAvailableFunds(amount: FiatAmount, fundsId: ExchangeId = ExchangeId.random())
                           (block: (TestProbe, ExchangeId) => Unit): Unit = {
      givenBlockedFunds(amount, fundsId) { (listener, funds) =>
        listener.expectMsg(PaymentProcessorActor.AvailableFunds(funds))
        block(listener, funds)
      }
    }

    def givenUnavailableFunds(amount: FiatAmount, fundsId: ExchangeId = ExchangeId.random())
                             (block: (TestProbe, ExchangeId) => Unit): Unit = {
      givenBlockedFunds(amount, fundsId) { (listener, funds) =>
        listener.expectMsg(PaymentProcessorActor.UnavailableFunds(funds))
        block(listener, funds)
      }
    }

    def setBalance(balance: FiatAmount): Unit = {
      actor ! BlockingFundsActor.BalancesUpdate(Seq(balance))
    }
  }
}
