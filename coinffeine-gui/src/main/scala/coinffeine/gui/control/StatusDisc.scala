package coinffeine.gui.control

import scalafx.beans.value.ObservableValue
import scalafx.scene.paint._
import scalafx.scene.shape.Circle

import coinffeine.gui.util.ScalafxImplicits._

class StatusDisc(statusProperty: ObservableValue[ConnectionStatus.Color, ConnectionStatus.Color])
  extends Circle {
  radius = 8
  fill.bind(statusProperty.delegate.map(status => StatusDisc.FillMapping(status)))
}

object StatusDisc {

  private val FillMapping = Map[ConnectionStatus.Color, Paint](
    ConnectionStatus.Red -> gradient("#ff2521", "#a10000"),
    ConnectionStatus.Yellow -> gradient("#fffdd7", "#b2b200"),
    ConnectionStatus.Green -> gradient("#ffffff", "#079400")
  )

  private def gradient(fromColor: String, toColor: String) =
    LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
      Stop(0, Color.valueOf(fromColor)),
      Stop(1, Color.valueOf(toColor))
    )
}
