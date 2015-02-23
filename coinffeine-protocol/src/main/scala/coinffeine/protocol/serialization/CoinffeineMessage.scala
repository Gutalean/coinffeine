package coinffeine.protocol.serialization

import coinffeine.protocol.messages.PublicMessage

sealed trait CoinffeineMessage
case class Payload(message: PublicMessage) extends CoinffeineMessage
