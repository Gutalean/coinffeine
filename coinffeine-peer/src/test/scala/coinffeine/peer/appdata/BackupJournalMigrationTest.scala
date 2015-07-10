package coinffeine.peer.appdata

import java.io.File
import scalaz.-\/
import scalaz.syntax.either._

import org.scalatest.{Inside, Outcome}

import coinffeine.common.test.{FixtureUnitTest, TempDir}
import coinffeine.peer.config.ConfigProvider
import coinffeine.peer.config.user.UserFileConfigProvider

class BackupJournalMigrationTest extends FixtureUnitTest with Inside {

  "Migration from 0.8 to 0.9" should "abort if user doesn't approve the migration" in { f =>
      f.context.givenUserDisapproval()
      f.migration.apply(f.context) shouldBe Migration.Aborted.left
    }

  it should "rename journal and snapshot directories if the user approves it" in { f =>
    f.context.givenUserApproval()
    f.context.givenEventSourcingDirectories()
    f.migration.apply(f.context) shouldBe Migration.Success
    f.context.expectNoEventSourcingDirectories()
    f.context.expectEventSourcingBackup()
  }

  it should "report failure if directories cannot be moved" in { f =>
    f.context.givenUserApproval()
    f.context.givenEventSourcingDirectories()
    f.context.givenPreviousEventSourcingBackup()
    inside(f.migration.apply(f.context)) {
      case -\/(Migration.Failed(cause)) => cause.getMessage should include("already exists")
    }
  }

  override type FixtureParam = Fixture

  override protected def withFixture(test: OneArgTest): Outcome = {
    TempDir.withTempDir("migrationTest") { dataDir =>
      test(new Fixture(dataDir))
    }
  }

  class Fixture(val dataDir: File) {
    val context = new MockedContext(dataDir)
    val migration = new BackupJournalMigration("v0.8")
  }

  class MockedContext(dataDir: File) extends Migration.Context {
    private var userApproves = false

    override val config: ConfigProvider = new UserFileConfigProvider(dataDir)

    private val eventSourcingDirectories = Seq(
      "akka.persistence.journal.leveldb.dir",
      "akka.persistence.snapshot-store.local.dir"
    ).map(setting => new File(config.enrichedConfig.getString(setting)))

    private val backupDirectories = eventSourcingDirectories.map { f =>
      new File(f.getAbsolutePath + ".v0.8")
    }

    override def confirm(title: String, question: String) = userApproves

    def givenUserApproval(): Unit = { userApproves = true }
    def givenUserDisapproval(): Unit = { userApproves = false }

    def givenEventSourcingDirectories(): Unit = {
      eventSourcingDirectories.foreach(_.mkdir())
    }

    def expectNoEventSourcingDirectories(): Unit = {
      eventSourcingDirectories.find(_.exists()) shouldBe 'empty
    }

    def expectEventSourcingBackup(): Unit = {
      backupDirectories.find(!_.exists()) shouldBe 'empty
    }

    def givenPreviousEventSourcingBackup(): Unit = {
      backupDirectories.foreach(_.mkdir())
    }
  }
}
