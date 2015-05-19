package coinffeine.common.akka.persistence

import scala.concurrent.duration._

import akka.actor.Cancellable
import akka.persistence.PersistentActor

/** Mixin for getting periodic messages to trigger snapshot creation */
trait PeriodicSnapshot extends PersistentActor {

  private var snapshotsTimer: Cancellable = _

  override def preStart(): Unit = {
    scheduleSnapshots()
    super.preStart()
  }

  override def postStop(): Unit = {
    super.postStop()
    snapshotsTimer.cancel()
  }

  private def scheduleSnapshots(): Unit = {
    import context.dispatcher
    snapshotsTimer = context.system.scheduler.schedule(
      initialDelay = PeriodicSnapshot.Interval,
      interval = PeriodicSnapshot.Interval,
      receiver = self,
      message = PeriodicSnapshot.CreateSnapshot
    )
  }
}

object PeriodicSnapshot {
  /** Interval between snapshots */
  private val Interval = 1.minute

  /** Snapshot-trigger self-message */
  case object CreateSnapshot
}
