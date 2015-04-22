package coinffeine.gui.wizard

import javafx.event.{EventTarget, Event}
import scalafx.event.EventType

class StepPaneEvent(source: Any, target: EventTarget) extends Event(StepPaneEvent.PaneActive)

object StepPaneEvent {

  object PaneActive extends EventType[StepPaneEvent]("PANE_ACTIVE")
}
