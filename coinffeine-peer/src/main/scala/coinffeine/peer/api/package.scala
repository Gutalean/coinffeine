package coinffeine.peer

package object api {

  type EventHandler = PartialFunction[CoinffeineApp.Event, Unit]
}
