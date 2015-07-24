package coinffeine.gui.application.launcher

import scala.concurrent.Future
import scala.util.control.NoStackTrace
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.ButtonType

import com.typesafe.scalalogging.LazyLogging

import coinffeine.gui.scene.CoinffeineAlert
import coinffeine.gui.util.FxExecutor
import coinffeine.peer.appdata.{MigrationPlan, DataVersion, Migration, Migrations}
import coinffeine.peer.config.ConfigProvider

class DataMigrationAction(configProvider: ConfigProvider) extends LazyLogging {

  private implicit val executor = FxExecutor.asContext

  private object Context extends Migration.Context {
    override val config = configProvider

    override def confirm(titleText: String, question: String): Boolean = {
      new CoinffeineAlert(AlertType.Confirmation, prefSize = Some(450, 300)) {
        headerText = titleText
        contentText = question
      }.showAndWait().contains(ButtonType.OK)
    }
  }

  def apply(): Future[Unit] = Future {
    planMigration.execute(Context)(handleMigrationError)
    updateDataVersion()
  }

  private def planMigration: MigrationPlan = Migrations.plan(configProvider.generalSettings())

  private def updateDataVersion(): Unit = {
    val updatedSettings = configProvider.generalSettings()
      .copy(dataVersion = Some(DataVersion.Current))
    configProvider.saveUserSettings(updatedSettings)
  }

  private def handleMigrationError(error: Migration.Error): Unit = error match {
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
