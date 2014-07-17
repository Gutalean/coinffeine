package coinffeine.peer

import coinffeine.peer.api.event.CoinffeineAppEvent

package object api {

  type EventHandler = PartialFunction[CoinffeineAppEvent, Unit]
}
