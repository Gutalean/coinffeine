package coinffeine.peer.appdata

import coinffeine.common.test.UnitTest
import coinffeine.peer.appdata.Migration.{Context, Result}
import coinffeine.peer.config.GeneralSettings

class MigrationPlannerTest extends UnitTest {

  val currentVersion = DataVersion(5)

  abstract class DummyMigration extends Migration {
    override def apply(context: Context): Result = ???
  }
  case object Migration1 extends DummyMigration
  case object Migration2 extends DummyMigration
  case object Migration3 extends DummyMigration

  val sampleMigrations = Map(
    DataVersion(1) -> Migration1,
    DataVersion(2) -> Migration2,
    DataVersion(4) -> Migration3
  )

  "Migration planner" should "plan no migration when versions match" in {
    val upToDateSettings = GeneralSettings(
      licenseAccepted = true,
      dataVersion = Some(currentVersion),
      serviceStartStopTimeout = null
    )
    new MigrationPlanner(sampleMigrations)
      .plan(currentVersion, upToDateSettings) shouldBe 'empty
  }

  it should "plan no migration when version and license settings are blank" in {
    val firstRunSettings = GeneralSettings(
      licenseAccepted = false,
      dataVersion = None,
      serviceStartStopTimeout = null
    )
    new MigrationPlanner(sampleMigrations)
      .plan(currentVersion, firstRunSettings) shouldBe 'empty
  }

  it should "plan no migration when there is a version mismatch but there are no migrations" in {
    new MigrationPlanner(Map.empty).plan(currentVersion, settingsForVersion(1)) shouldBe 'empty
  }

  it should "plan a migration from pre-0.9 saved settings" in {
    val settingsWithoutExplicitVersion = GeneralSettings(
      licenseAccepted = true,
      dataVersion = None,
      serviceStartStopTimeout = null
    )
    new MigrationPlanner(sampleMigrations)
      .plan(DataVersion(2), settingsWithoutExplicitVersion) shouldBe Seq(Migration1)
  }

  it should "plan all the existing migrations from the saved version to the current version" in {
    new MigrationPlanner(sampleMigrations)
      .plan(currentVersion, settingsForVersion(2)) shouldBe Seq(Migration2, Migration3)
  }

  def settingsForVersion(savedVersion: Int) = GeneralSettings(
    licenseAccepted = true,
    dataVersion = Some(DataVersion(savedVersion)),
    serviceStartStopTimeout = null
  )
}
