package coinffeine.peer.appdata

import scalaz.Scalaz._
import java.io.{IOException, File}

import scalaz.\/

import org.apache.commons.io.FileUtils

import coinffeine.peer.appdata.Migration.Context

object MigrationV1ToV2 extends Migration {

  private val ExplanationTitle = "Order data migration"

  private val Explanation = "Saved order data corresponds to an older Coinffeine version. " +
    "Orders in progress cannot be continued with this version so they will be pruned " +
    "(a backup will be made).\n\n" +
    "Do you want to continue?"

  override def apply(context: Context) =
    if (context.confirm(ExplanationTitle, Explanation)) moveEventSourcingDirectories(context)
    else Migration.Aborted.left

  private def moveEventSourcingDirectories(context: Migration.Context): Migration.Result = {
    val eventSourcingDirectories = Seq(
      "akka.persistence.journal.leveldb.dir",
      "akka.persistence.snapshot-store.local.dir"
    ).map(setting => new File(context.config.enrichedConfig.getString(setting)))

    eventSourcingDirectories.foldLeft[Migration.Result](Migration.Success) { (accum, sourceDir) =>
      accum >> {
        val destDir = new File(sourceDir.getAbsolutePath + ".v0.8")
        \/.fromTryCatchNonFatal(FileUtils.moveDirectory(sourceDir, destDir))
          .leftMap(Migration.Failed.apply)
      }
    }
  }
}
