package coinffeine.common.akka.event

/** A type class that provides a topic for a given event type. */
trait TopicProvider[T] {
  implicit val me: TopicProvider[T] = this
  val Topic: String
}
