package coinffeine.gui.control

import scalafx.beans.value.ObservableValue
import scalafx.scene.paint._
import scalafx.scene.shape.Circle

import coinffeine.gui.util.ScalafxImplicits._

class StatusDisc(statusProperty: ObservableValue[StatusDisc.Status, StatusDisc.Status])
  extends Circle {
  radius = 8
  fill.bind(statusProperty.delegate.map(_.fill))
}

object StatusDisc {

  sealed trait Status {
    val fill: Paint
  }

  case object Red extends Status {
    override val fill = gradient("#ff2521", "#a10000")
  }

  case object Yellow extends Status {
    override val fill = gradient("#fffdd7", "#b2b200")
  }

  case object Green extends Status {
    override val fill = gradient("#ffffff", "#079400")
  }

  private def gradient(fromColor: String, toColor: String) =
    LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
      Stop(0, Color.valueOf(fromColor)),
      Stop(1, Color.valueOf(toColor))
    )
}
