package coinffeine.common.akka.persistence

import scala.concurrent.duration._

import akka.actor.{ActorLogging, Cancellable}
import akka.persistence.{PersistentActor, SaveSnapshotSuccess, SnapshotSelectionCriteria}

/** Mixin for getting periodic messages to trigger snapshot creation */
trait PeriodicSnapshot extends PersistentActor with ActorLogging {

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

  protected def deletingSnapshots: Receive = {
    case SaveSnapshotSuccess(metadata) =>
      log.debug("Snapshot {}/{} saved successfully, removing older ones",
        metadata.persistenceId, metadata.sequenceNr)
      deleteMessages(metadata.sequenceNr)
      deleteSnapshots(SnapshotSelectionCriteria(metadata.sequenceNr - 1, metadata.timestamp))
  }
}

object PeriodicSnapshot {
  /** Interval between snapshots */
  private val Interval = 1.minute

  /** Snapshot-trigger self-message */
  case object CreateSnapshot
}
