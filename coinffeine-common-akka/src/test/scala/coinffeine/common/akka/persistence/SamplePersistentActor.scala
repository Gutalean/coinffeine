package coinffeine.common.akka.persistence

import akka.persistence.PersistentActor

class SamplePersistentActor(override val persistenceId: String) extends PersistentActor {

  import SamplePersistentActor._

  var sum = 0

  override def receiveRecover: Receive = {
    case AddedNumber(n) => sum += n
  }

  override def receiveCommand: Receive = {
    case AddNumber(n) =>
      persist(AddedNumber(n)){ event =>
        sum += n
      }

    case RequestSum => sender() ! Sum(sum)
  }
}

object SamplePersistentActor {
  case class AddNumber(n: Int)
  case object RequestSum
  case class Sum(n: Int)

  private case class AddedNumber(n: Int)
}
