package coinffeine.peer.appdata

import java.io.File
import scalaz.-\/
import scalaz.syntax.either._

import org.scalatest.Inside

import coinffeine.common.test.{TempDir, UnitTest}
import coinffeine.peer.config.ConfigProvider
import coinffeine.peer.config.user.UserFileConfigProvider

class BackupJournalMigrationTest extends UnitTest with Inside {

  "Migration from 0.8 to 0.9" should "abort if user doesn't approve the migration" in
    new Fixture {
      context.givenUserDisapproval()
      migration.apply(context) shouldBe Migration.Aborted.left
    }

  it should "rename journal and snapshot directories if the user approves it" in new Fixture {
    context.givenUserApproval()
    context.givenEventSourcingDirectories()
    migration.apply(context) shouldBe Migration.Success
    context.expectNoEventSourcingDirectories()
    context.expectEventSourcingBackup()
  }

  it should "report failure if directories cannot be moved" in new Fixture {
    context.givenUserApproval()
    context.givenEventSourcingDirectories()
    context.givenPreviousEventSourcingBackup()
    inside(migration.apply(context)) {
      case -\/(Migration.Failed(cause)) => cause.getMessage should include("already exists")
    }
  }

  trait Fixture {
    protected val dataDir = TempDir.create("migrationTest")
    protected val context = new MockedContext(dataDir)
    protected val migration = new BackupJournalMigration("v0.8")
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
