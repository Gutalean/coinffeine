package coinffeine.peer.exchange.fake

import akka.actor.{Props, Actor}

import coinffeine.peer.exchange.ExchangeActor

/** Performs a fake exchange for demo purposes */
class FakeExchangeActor extends Actor {
  // TODO: not yet implemented
  override def receive: Receive = ???
}

object FakeExchangeActor {
  trait Component extends ExchangeActor.Component {
    override def exchangeActorProps: Props = Props(new FakeExchangeActor())
  }
}
