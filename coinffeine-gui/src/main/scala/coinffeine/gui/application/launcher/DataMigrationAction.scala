package coinffeine.gui.application.launcher

import scala.concurrent.Future
import scala.util.control.NoStackTrace
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.{Alert, ButtonType}

import com.typesafe.scalalogging.LazyLogging

import coinffeine.gui.util.FxExecutor
import coinffeine.peer.appdata.{DataVersion, Migration, Migrations}
import coinffeine.peer.config.ConfigProvider

class DataMigrationAction(configProvider: ConfigProvider) extends LazyLogging {

  private implicit val executor = FxExecutor.asContext

  private object Context extends Migration.Context {
    override val config = configProvider

    override def confirm(titleText: String, question: String): Boolean = {
      new Alert(AlertType.Confirmation) {
        headerText = titleText
        contentText = question
      }.showAndWait().contains(ButtonType.OK)
    }
  }

  def apply(): Future[Unit] = Future {
    val plan = planMigration
    executeMigration(plan)
    updateDataVersion()
  }

  private def planMigration: Seq[Migration] = Migrations.plan(configProvider.generalSettings())

  private def executeMigration(plan: Seq[Migration]): Unit = {
    for (migration <- plan) {
      migration.apply(Context).valueOr(handleMigrationErrors)
    }
  }

  private def updateDataVersion(): Unit = {
    val updatedSettings = configProvider.generalSettings()
      .copy(dataVersion = Some(DataVersion.Current))
    configProvider.saveUserSettings(updatedSettings)
  }

  private def handleMigrationErrors(error: Migration.Error): Unit = error match {
    case Migration.Aborted => throw DataMigrationAction.AbortedByUser

    case Migration.Failed(cause) =>
      logger.error("Migration failed", cause)
      throw cause
  }
}

object DataMigrationAction {
  case object AbortedByUser
    extends RuntimeException("Migration aborted by user") with NoStackTrace
}
