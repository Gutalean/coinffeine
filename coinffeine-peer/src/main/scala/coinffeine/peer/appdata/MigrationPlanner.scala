package coinffeine.peer.appdata

import coinffeine.peer.config.GeneralSettings

/** Selects migrations to update saved data.
  *
  * @constructor
  * @param migrations  Map of migrations whose key is the starting point of the migration
  */
class MigrationPlanner(migrations: Map[DataVersion, Migration]) {

  /** Determine what sequence of migrations will update the data dir to the
    * current [[DataVersion]]
    */
  def plan(currentVersion: DataVersion, settings: GeneralSettings): Seq[Migration] = {
    (settings.dataVersion, settings.licenseAccepted) match {
      case (None, true) => migrate(DataVersion(1), currentVersion)
      case (Some(previousVersion), _) => migrate(previousVersion, currentVersion)
      case _ => Seq.empty
    }
  }

  private def migrate(from: DataVersion,
                      to: DataVersion,
                      plan: Seq[Migration] = Seq.empty): Seq[Migration] =
    migrations.filterKeys(ver => ver >= from && ver < to)
      .toSeq
      .sortBy(_._1)
      .map(_._2)
}
