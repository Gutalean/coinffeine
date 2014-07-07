package com.coinffeine.gui.application.operations

import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.scene.control._
import scalafx.scene.layout._

import com.coinffeine.client.api.CoinffeineApp
import com.coinffeine.gui.application.ApplicationView

class OperationsView(app: CoinffeineApp) extends ApplicationView {

  private val newOrderButton = new Button {
    text = "New order"
    handleEvent(ActionEvent.ACTION) { () =>
      val form = new OrderSubmissionForm(app)
      form.show(delegate.getScene.getWindow)
    }
  }

  override def name: String = "Operations"

  override def centerPane: Pane = new StackPane {
    content = newOrderButton
  }

}
