package coinffeine.peer.appdata

import coinffeine.peer.appdata.Migration.Context

sealed trait MigrationPlan {

  def needed: Boolean

  def execute(context: Migration.Context)(errorHandler: Migration.Error => Unit): Unit
}

/** An ordered sequence of migrations */
case class SequentialMigration(steps: Seq[Migration]) extends MigrationPlan {

  override def needed = steps.nonEmpty

  override def execute(context: Context)(errorHandler: (Migration.Error) => Unit): Unit = {

    def runSteps(remaining: Seq[Migration]): Unit = {
      if (remaining.nonEmpty) {
        remaining.head.apply(context).fold(
          l = errorHandler,
          r = _ => runSteps(remaining.tail)
        )
      }
    }

    runSteps(steps)
  }
}

object SequentialMigration {
  val empty = SequentialMigration(Seq.empty)
}
