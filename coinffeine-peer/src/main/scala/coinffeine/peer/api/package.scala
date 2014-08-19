package coinffeine.peer

import coinffeine.model.event.CoinffeineAppEvent

package object api {

  type EventHandler = PartialFunction[CoinffeineAppEvent, Unit]
}
