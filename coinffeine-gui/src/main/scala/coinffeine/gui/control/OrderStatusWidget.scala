package coinffeine.gui.control

import scalafx.beans.property.ObjectProperty
import scalafx.css.PseudoClass
import scalafx.scene.control.Label
import scalafx.scene.layout.{HBox, StackPane, VBox}

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.scene.styles.NodeStyles
import coinffeine.model.exchange.Exchange
import coinffeine.model.market._

/** Interactively shows the status of an order if you bind the {{{status}}} property. */
class OrderStatusWidget extends VBox {
  import OrderStatusWidget._

  /** Property representing the status or an order */
  val status = new ObjectProperty[OrderStatusWidget.Status](this, "status", Submitting)

  private val spinner = new Spinner
  private val label = new Label() {
    styleClass += "message"
    text <== status.delegate.mapToString(_.message)
  }
  private val sections = Vector.tabulate(3) { index =>
    new StackPane with NodeStyles.HExpand {
      styleClass += s"section${index + 1}"
    }
  }

  styleClass += "order-status"
  visible <== status.delegate.mapToBool(_ != Completed)
  content = Seq(label, new HBox {
    styleClass += "bar"
    content = sections
  })

  status.onChange { (observable, prevStatus, nextStatus) =>
    updateSectionFilling(nextStatus.filledSections)
    updateSpinner(prevStatus.spinnerSection, nextStatus.spinnerSection)
  }

  private def updateSectionFilling(filledSections: Int): Unit = {
    for ((section, index) <- sections.zipWithIndex) {
      section.delegate.pseudoClassStateChanged(FilledClass, index < filledSections)
    }
  }

  private def updateSpinner(prevSection: Option[Int], nextSection: Option[Int]): Unit = {
    if (prevSection == nextSection) return
    moveSpinner(prevSection, nextSection)
    updateAnimation(prevSection, nextSection)
  }

  private def moveSpinner(prevSection: Option[Int], nextSection: Option[Int]): Unit = {
    prevSection.foreach(index => sections(index).content.clear())
    nextSection.foreach(index => sections(index).content.add(spinner))
  }

  private def updateAnimation(prevSection: Option[Int], nextSection: Option[Int]): Unit =
    (prevSection.isDefined, nextSection.isDefined) match {
      case (true, false) => spinner.stop()
      case (false, true) => spinner.play()
      case _ => // Keep it playing/stopped
    }
}

object OrderStatusWidget {
  sealed trait Status {
    def message: String = ""
    def filledSections: Int = 0
    def spinnerSection: Option[Int] = None
  }

  object Status {
    def fromOrder(order: AnyCurrencyOrder): Status =
      order.status match {
        case NotStartedOrder if !order.inMarket => Submitting
        case NotStartedOrder => InMarket
        case InProgressOrder =>
          if (order.exchanges.values.exists(isInProgress)) InProgress else Matching
        case CompletedOrder | CancelledOrder => Completed
      }

    private def isInProgress(exchange: Exchange[_]): Boolean =
      !exchange.isCompleted && exchange.progress.bitcoinsTransferred.buyer.isPositive
  }

  case object Submitting extends Status {
    override val message = "Submitting"
  }

  case object InMarket extends Status {
    override val message = "In market"
    override val spinnerSection = Some(0)
    override val filledSections = 1
  }

  case object Matching extends Status {
    override val message = "Matching"
    override val spinnerSection = Some(1)
    override val filledSections = 2
  }

  case object InProgress extends Status {
    override val message = "In progress"
    override val spinnerSection = Some(2)
    override val filledSections = 3
  }

  case object Completed extends Status

  private val FilledClass = PseudoClass("filled")
}
