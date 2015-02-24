package coinffeine.protocol.serialization

import coinffeine.protocol.Version
import coinffeine.protocol.messages.PublicMessage

sealed trait CoinffeineMessage

case class Payload(message: PublicMessage) extends CoinffeineMessage

case class ProtocolMismatch(supportedVersion: Version) extends CoinffeineMessage
