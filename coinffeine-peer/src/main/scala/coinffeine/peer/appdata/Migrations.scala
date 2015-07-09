package coinffeine.peer.appdata

import coinffeine.peer.config.GeneralSettings

object Migrations {
  type Transition = (DataVersion, DataVersion)

  val Planner = new MigrationPlanner(migrations = Map(
    DataVersion(1) -> new BackupJournalMigration("v0.8"),
    DataVersion(2) -> new BackupJournalMigration("v0.9")
  ))

  def plan(settings: GeneralSettings): Seq[Migration] =
    Planner.plan(DataVersion.Current, settings)
}
