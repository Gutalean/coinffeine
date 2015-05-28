package coinffeine.peer.appdata

import coinffeine.peer.config.GeneralSettings

object Migrations {
  type Transition = (DataVersion, DataVersion)

  val Planner = new MigrationPlanner(migrations = Map(
    DataVersion(1) -> MigrationV1ToV2
  ))

  def plan(settings: GeneralSettings): Seq[Migration] =
    Planner.plan(DataVersion.Current, settings)
}
