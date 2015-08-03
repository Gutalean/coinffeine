package coinffeine.gui.control

import scalafx.beans.property.{BooleanProperty, ObjectProperty}
import scalafx.css.PseudoClass
import scalafx.scene.control.Label
import scalafx.scene.layout.{HBox, StackPane, VBox}

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.scene.styles.NodeStyles
import coinffeine.model.exchange.Exchange
import coinffeine.model.order._

/** Interactively shows the status of an order if you bind the {{{status}}} property. */
class OrderStatusWidget extends VBox {
  import OrderStatusWidget._

  /** Property representing the status or an order */
  val status = new ObjectProperty[OrderStatusWidget.Status](this, "status", Offline)

  /** Property representing lack of connectivity with the Coinffeine network */
  val online = new BooleanProperty(this, "online", true)

  private val spinner = new Spinner(autoPlay = true)
  private val label = new Label() {
    styleClass += "message"
    text <== status.delegate.map(_.message).toStr
  }
  private val sections = Vector.tabulate(3) { index =>
    new StackPane with NodeStyles.HExpand {
      styleClass += s"section${index + 1}"
    }
  }

  styleClass += "order-status"
  visible <== status.delegate.map(_ != Completed).toBool.and(online)
  children = Seq(label, new HBox {
    styleClass += "bar"
    children = sections
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
    prevSection.foreach(index => sections(index).children.clear())
    nextSection.foreach(index => sections(index).children.add(spinner))
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
    def fromOrder(order: Order): Status =
      order.status match {
        case OrderStatus.NotStarted => submittingStatus(order)
        case OrderStatus.InProgress if !hasActiveExchanges(order) => submittingStatus(order)
        case OrderStatus.InProgress =>
          if (activeExchanges(order).forall(isWaitingForConfirmation)) WaitingForConfirmation
          else if (order.exchanges.values.exists(isInProgress)) InProgress
          else Matching
        case OrderStatus.Completed | OrderStatus.Cancelled => Completed
      }

    private def hasActiveExchanges(order: Order) =
      activeExchanges(order).nonEmpty

    private def activeExchanges(order: Order) =
      order.exchanges.values.filterNot(_.isCompleted)

    private def submittingStatus(order: Order) =
      if (!order.inMarket) Offline else InMarket

    private def isWaitingForConfirmation(exchange: Exchange): Boolean =
      exchange.exchangedBitcoin.buyer == exchange.progress.bitcoinsTransferred.buyer

    private def isInProgress(exchange: Exchange): Boolean =
      !exchange.isCompleted && exchange.progress.bitcoinsTransferred.buyer.isPositive
  }

  case object Offline extends Status {
    override val message = "Offline"
    override val spinnerSection = None
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

  case object WaitingForConfirmation extends Status {
    override val message = "Waiting confirmation"
    override val spinnerSection = Some(2)
    override val filledSections = 3
  }

  case object Completed extends Status

  private val FilledClass = PseudoClass("filled")
}
