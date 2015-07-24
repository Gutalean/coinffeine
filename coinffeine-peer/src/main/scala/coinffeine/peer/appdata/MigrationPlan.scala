package coinffeine.peer.appdata

import scala.util.control.NoStackTrace

import coinffeine.peer.appdata.Migration.Context

sealed trait MigrationPlan {

  def needed: Boolean

  def execute(context: Migration.Context)(errorHandler: Migration.Error => Unit): Unit
}

/** An ordered sequence of migrations */
case class SequentialPlan(steps: Seq[Migration]) extends MigrationPlan {

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

case object FailToDowngradePlan extends MigrationPlan {

  override def needed = true

  override def execute(context: Context)(errorHandler: (Migration.Error) => Unit): Unit = {
    val message =
      ("Your data directory has version %s but this version of " +
          "Coinffeine supports up to version %d").format(
        formatVersion(context.config.generalSettings().dataVersion),
        DataVersion.Current.value
      )
    errorHandler(Migration.Failed(new RuntimeException(message) with NoStackTrace))
  }

  private def formatVersion(maybeVersion: Option[DataVersion]) =
    maybeVersion.fold("undefined")(_.value.toString)
}

object SequentialPlan {
  val empty = SequentialPlan(Seq.empty)
}
