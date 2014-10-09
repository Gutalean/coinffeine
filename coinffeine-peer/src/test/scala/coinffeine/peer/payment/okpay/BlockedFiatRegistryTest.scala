package coinffeine.peer.payment.okpay

import akka.actor.Props
import akka.testkit.TestProbe

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId
import coinffeine.peer.payment.PaymentProcessorActor

class BlockedFiatRegistryTest extends AkkaSpec {

  "The blocking funds actor" must "retrieve no blocked funds when no funds are blocked" in
    new WithBlockingFundsActor {
      actor ! BlockedFiatRegistry.RetrieveTotalBlockedFunds(Euro)
      expectMsg(BlockedFiatRegistry.TotalBlockedFunds(Euro.Zero))
    }

  it must "retrieve blocked funds when blocked" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    givenAvailableFunds(100.EUR) { funds =>
      actor ! BlockedFiatRegistry.RetrieveTotalBlockedFunds(Euro)
      expectMsg(BlockedFiatRegistry.TotalBlockedFunds(100.EUR))
    }
  }

  it must "retrieve no blocked funds after unblocked" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    givenAvailableFunds(100.EUR) { funds =>
      actor ! PaymentProcessorActor.UnblockFunds(funds)
      actor ! BlockedFiatRegistry.RetrieveTotalBlockedFunds(Euro)
      expectMsg(BlockedFiatRegistry.TotalBlockedFunds(Euro.Zero))
    }
  }

  it must "retrieve blocked funds after using some" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    givenAvailableFunds(100.EUR) { funds =>
      actor ! BlockedFiatRegistry.UseFunds(funds, 60.EUR)
      expectMsgPF() { case BlockedFiatRegistry.FundsUsed(`funds`, _) => }
      actor ! BlockedFiatRegistry.RetrieveTotalBlockedFunds(Euro)
      expectMsg(BlockedFiatRegistry.TotalBlockedFunds(40.EUR))
    }
  }

  it must "block funds up to existing balances" in new WithBlockingFundsActor {
    actor ! BlockedFiatRegistry.BalancesUpdate(Seq(100.EUR))
    actor ! PaymentProcessorActor.BlockFunds(ExchangeId.random(), 50.EUR)
    actor ! PaymentProcessorActor.BlockFunds(ExchangeId.random(), 50.EUR)
    actor ! PaymentProcessorActor.BlockFunds(ExchangeId.random(), 50.EUR)
    expectMsgAllConformingOf(
      classOf[PaymentProcessorActor.BlockedFunds],
      classOf[PaymentProcessorActor.BlockedFunds],
      classOf[PaymentProcessorActor.BlockedFunds]
    )
    eventProbe.expectMsgAllConformingOf(
      classOf[PaymentProcessorActor.AvailableFunds],
      classOf[PaymentProcessorActor.AvailableFunds],
      classOf[PaymentProcessorActor.UnavailableFunds]
    )
  }

  it must "reject blocking funds twice for the same exchange" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    actor ! PaymentProcessorActor.BlockFunds(ExchangeId("ex"), 50.EUR)
    expectMsgType[PaymentProcessorActor.BlockedFunds]
    eventProbe.expectMsgType[PaymentProcessorActor.AvailableFunds]
    actor ! PaymentProcessorActor.BlockFunds(ExchangeId("ex"), 20.EUR)
    expectMsgType[PaymentProcessorActor.AlreadyBlockedFunds]
  }

  it must "unblock blocked funds to make then available again" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    givenAvailableFunds(100.EUR) { funds =>
      actor ! PaymentProcessorActor.UnblockFunds(funds)
    }
    givenBlockedFunds(100.EUR) { funds =>
      expectBecomingAvailable(funds)
    }
  }

  it must "notify unavailable funds once block submission when insufficient funds" in
    new WithBlockingFundsActor {
      setBalance(100.EUR)
      givenBlockedFunds(110.EUR) { funds =>
        expectBecomingUnavailable(funds)
      }
    }

  it must "notify unavailable funds to the last ones when blocks exceeds the funds" in
    new WithBlockingFundsActor {
      setBalance(100.EUR)
      givenAvailableFunds(90.EUR) { funds1 =>
        givenAvailableFunds(10.EUR) { funds2 =>
          setBalance(90.EUR)
          expectBecomingUnavailable(funds2)

          setBalance(50.EUR)
          expectBecomingUnavailable(funds1)
        }
      }
    }

  it must "notify available funds when balance is enough again due to external increase" in
    new WithBlockingFundsActor {
      setBalance(100.EUR)
      givenAvailableFunds(100.EUR) { funds =>
        setBalance(50.EUR)
        expectBecomingUnavailable(funds)
        setBalance(100.EUR)
        expectBecomingAvailable(funds)
      }
    }

  it must "notify available funds when balance is enough again due to unblocking" in
    new WithBlockingFundsActor {
      setBalance(100.EUR)
      givenAvailableFunds(50.EUR) { funds1 =>
        givenAvailableFunds(50.EUR) { funds2 =>
          setBalance(60.EUR)
          expectBecomingUnavailable(funds2)
          actor ! PaymentProcessorActor.UnblockFunds(funds1)
          expectBecomingAvailable(funds2)
        }
      }
    }

  it must "reject funds usage for unknown funds id" in new WithBlockingFundsActor {
    val unknownFunds = ExchangeId("unknown")
    actor ! BlockedFiatRegistry.UseFunds(unknownFunds, 10.EUR)
    expectMsgPF() { case BlockedFiatRegistry.CannotUseFunds(`unknownFunds`, _, _) => }
  }

  it must "reject funds usage when it exceeds blocked amount" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    givenAvailableFunds(50.EUR) { funds =>
      actor ! BlockedFiatRegistry.UseFunds(funds, 100.EUR)
      expectMsgPF() { case BlockedFiatRegistry.CannotUseFunds(`funds`, _, _) => }
    }
  }

  it must "reject funds usage when block is unavailable" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    givenUnavailableFunds(150.EUR) { funds =>
      actor ! BlockedFiatRegistry.UseFunds(funds, 100.EUR)
      expectMsgPF() { case BlockedFiatRegistry.CannotUseFunds(`funds`, _, _) => }
    }
  }

  it must "accept funds usage when amount is less than blocked funds" in
    new WithBlockingFundsActor {
      setBalance(100.EUR)
      givenAvailableFunds(50.EUR) { funds =>
        actor ! BlockedFiatRegistry.UseFunds(funds, 10.EUR)
        expectMsgPF() { case BlockedFiatRegistry.FundsUsed(`funds`, _) => }
      }
    }

  it must "accept funds usage when amount is equals to blocked funds" in
    new WithBlockingFundsActor {
      setBalance(100.EUR)
      givenAvailableFunds(50.EUR) { funds =>
        actor ! BlockedFiatRegistry.UseFunds(funds, 50.EUR)
        expectMsgPF() { case BlockedFiatRegistry.FundsUsed(`funds`, _) => }
      }
    }

  it must "consider new balance after funds are used" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    givenAvailableFunds(50.EUR) { funds1 =>
      actor ! BlockedFiatRegistry.UseFunds(funds1, 10.EUR)
      expectMsgPF() { case BlockedFiatRegistry.FundsUsed(`funds1`, _) => }
      givenBlockedFunds(60.EUR) { funds2 =>
        expectBecomingUnavailable(funds2)
      }
    }
  }

  val funds1, funds2 = ExchangeId.random()

  it must "persist its state" in new WithBlockingFundsActor {
    setBalance(100.EUR)
    actor ! PaymentProcessorActor.BlockFunds(funds1, 60.EUR)
    actor ! PaymentProcessorActor.BlockFunds(funds2, 40.EUR)
    expectMsgAllOf(
      PaymentProcessorActor.BlockedFunds(funds1),
      PaymentProcessorActor.BlockedFunds(funds2)
    )
    actor ! PaymentProcessorActor.UnblockFunds(funds2)
    actor ! BlockedFiatRegistry.UseFunds(funds1, 20.EUR)
    expectMsg(BlockedFiatRegistry.FundsUsed(funds1, 20.EUR))
    system.stop(actor)
  }

  it must "recover its previous state" in new WithBlockingFundsActor(persistentId = lastId) {
    setBalance(100.EUR)
    expectBecomingAvailable(funds1)
    actor ! BlockedFiatRegistry.RetrieveTotalBlockedFunds(Euro)
    expectMsg(BlockedFiatRegistry.TotalBlockedFunds(40.EUR))
    system.stop(actor)
  }

  private abstract class WithBlockingFundsActor(persistentId: Int = freshId()) {
    val actor = system.actorOf(Props(new BlockedFiatRegistry(persistentId.toString)))

    val eventProbe = TestProbe()
    system.eventStream.subscribe(
      eventProbe.ref, classOf[PaymentProcessorActor.FundsAvailabilityEvent])

    def givenBlockedFunds(amount: FiatAmount, fundsId: ExchangeId = ExchangeId.random())
                         (block: ExchangeId => Unit): Unit = {
      val listener = TestProbe()
      listener.send(actor, PaymentProcessorActor.BlockFunds(fundsId, amount))
      listener.expectMsgType[PaymentProcessorActor.BlockedFunds]
      block(fundsId)
    }

    def givenAvailableFunds(amount: FiatAmount, fundsId: ExchangeId = ExchangeId.random())
                           (block: ExchangeId => Unit): Unit = {
      givenBlockedFunds(amount, fundsId) { _ =>
        expectBecomingAvailable(fundsId)
        block(fundsId)
      }
    }

    def givenUnavailableFunds(amount: FiatAmount, fundsId: ExchangeId = ExchangeId.random())
                             (block: ExchangeId => Unit): Unit = {
      givenBlockedFunds(amount, fundsId) { _ =>
        expectBecomingUnavailable(fundsId)
        block(fundsId)
      }
    }

    def setBalance(balance: FiatAmount): Unit = {
      actor ! BlockedFiatRegistry.BalancesUpdate(Seq(balance))
    }

    def expectBecomingAvailable(fundsId: ExchangeId): Unit = {
      eventProbe.expectMsg(PaymentProcessorActor.AvailableFunds(fundsId))
    }

    def expectBecomingUnavailable(fundsId: ExchangeId): Unit = {
      eventProbe.expectMsg(PaymentProcessorActor.UnavailableFunds(fundsId))
    }
  }

  private var lastId = 0
  private def freshId(): Int = {
    lastId += 1
    lastId
  }
}
