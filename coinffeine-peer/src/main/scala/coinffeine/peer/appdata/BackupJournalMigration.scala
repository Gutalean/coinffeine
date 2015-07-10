package coinffeine.peer.appdata

import java.io.File
import scalaz.\/
import scalaz.syntax.either._
import scalaz.syntax.monad._

import org.apache.commons.io.FileUtils

import coinffeine.peer.appdata.Migration.Context

class BackupJournalMigration(backupSuffix: String) extends Migration {

  private val ExplanationTitle = "Order data migration"

  private val Explanation = "Saved order data corresponds to an older Coinffeine version. " +
    "Orders in progress cannot be continued with this version so they will be pruned " +
    "(a backup will be made).\n\n" +
    "Do you want to continue?"

  override def apply(context: Context) = {
    val directories = eventSourcingDirectories(context)
    if (!shouldMove(directories)) Migration.Success
    else if (context.confirm(ExplanationTitle, Explanation)) moveDirectories(directories)
    else Migration.Aborted.left
  }

  private def shouldMove(directories: Seq[File]): Boolean = directories.exists(_.exists())

  private def moveDirectories(directories: Seq[File]): Migration.Result = directories
      .filter(_.exists())
      .foldLeft(Migration.Success) { (accum, sourceDir) =>
          accum >> moveDirectory(sourceDir)
      }

  private def eventSourcingDirectories(context: Migration.Context): Seq[File] = Seq(
    "akka.persistence.journal.leveldb.dir",
    "akka.persistence.snapshot-store.local.dir"
  ).map(setting => new File(context.config.enrichedConfig.getString(setting)))

  private def moveDirectory(sourceDir: File): Migration.Result = {
    val destDir = new File(sourceDir.getAbsolutePath + "." + backupSuffix)
    \/.fromTryCatchNonFatal(FileUtils.moveDirectory(sourceDir, destDir))
      .leftMap(Migration.Failed.apply)
  }
}
