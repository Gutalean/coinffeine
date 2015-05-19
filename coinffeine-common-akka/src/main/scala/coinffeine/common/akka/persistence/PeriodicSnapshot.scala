package coinffeine.common.akka.persistence

import scala.concurrent.duration._

import akka.actor.{ActorLogging, Cancellable}
import akka.persistence._

/** Mixin for getting periodic messages to trigger snapshot creation */
trait PeriodicSnapshot extends PersistentActor with ActorLogging {

  private var snapshotsTimer: Cancellable = _
  private var lastSnapshotNr: Option[Long] = None

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

  protected def createSnapshot: Option[PersistentEvent]

  protected def managingSnapshots: Receive = {

    case PeriodicSnapshot.CreateSnapshot if lastSnapshotNr.forall(_ < lastSequenceNr) =>
      createSnapshot.foreach { snapshot =>
        log.debug("Saving snapshot {} for {}", lastSequenceNr, persistenceId)
        saveSnapshot(snapshot)
      }

    case SaveSnapshotSuccess(metadata) =>
      log.debug("Snapshot {} for {} saved successfully, removing older ones",
        metadata.sequenceNr, metadata.persistenceId)
      lastSnapshotNr = Some(metadata.sequenceNr)
      deleteMessages(metadata.sequenceNr)
      if (metadata.sequenceNr > 0) {
        deleteSnapshots(SnapshotSelectionCriteria(metadata.sequenceNr - 1, metadata.timestamp))
      }

    case SaveSnapshotFailure(metadata, cause) =>
      log.error(cause, "Cannot save snapshot {}", metadata)
  }
}

object PeriodicSnapshot {
  /** Interval between snapshots */
  private val Interval = 1.minute

  /** Snapshot-trigger self-message */
  case object CreateSnapshot
}
