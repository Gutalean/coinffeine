package com.coinffeine.client

package object api {

  type EventHandler = PartialFunction[CoinffeineApp.Event, Unit]
}
