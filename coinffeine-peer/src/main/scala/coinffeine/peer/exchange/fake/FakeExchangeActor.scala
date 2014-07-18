package coinffeine.peer.exchange.fake

import scala.concurrent.duration._

import akka.actor._

import coinffeine.peer.exchange.ExchangeActor

/** Performs a fake exchange for demo purposes */
class FakeExchangeActor extends Actor with ActorLogging {

  override def receive: Receive = {
    case init: ExchangeActor.StartExchange =>
      new InitializedExchange(init, sender()).start()
  }

  private class InitializedExchange(init: ExchangeActor.StartExchange, listener: ActorRef) {
    import init._

    def start(): Unit = {
      logEvent("Handshake success")
      context.setReceiveTimeout(2.seconds)
      context.become(handlingStep(0))
    }

    def handlingStep(step: Int): Receive = {
      case ReceiveTimeout if step == exchange.amounts.breakdown.totalSteps =>
        logEvent(s"Exchange finished successfully")
        listener ! ExchangeActor.ExchangeSuccess
        context.become(finished)

      case ReceiveTimeout =>
        logEvent(s"Step $step finished")
        context.become(handlingStep(step + 1))
    }

    val finished: Receive = {
      case _ =>
    }

    private def logEvent(message: String): Unit = {
      log.info("{}: {}", exchange.id, message)
    }
  }
}

object FakeExchangeActor {
  trait Component extends ExchangeActor.Component {
    override def exchangeActorProps: Props = Props(new FakeExchangeActor())
  }
}
