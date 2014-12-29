package coinffeine.gui.setup

import javafx.scene.Node
import javafx.scene.control.Label

import coinffeine.gui.GuiTest

class PasswordStepPaneTest extends GuiTest[PasswordStepPane]  {

  override def createRootNode(): PasswordStepPane = new PasswordStepPane()

  "The password step pane" should "disable the password fields if no password is selected" in
    new Fixture {
      click("#noPassword")
      find[Node]("#repeatPasswordField") shouldBe 'disabled
      find[Node]("#passwordField") shouldBe 'disabled
      instance.canContinue.value shouldBe true
    }

  it should "re-enable the password fields if the password option is re-selected" in new Fixture {
    click("#noPassword")
    find[Node]("#repeatPasswordField") shouldBe 'disabled
    find[Node]("#passwordField") shouldBe 'disabled
    click("#usePassword")
    find[Node]("#repeatPasswordField") should not be 'disabled
    find[Node]("#passwordField") should not be 'disabled
    instance.canContinue.value shouldBe false
  }

  it should "warn the user about weak passwords" in new Fixture {
    find[Label]("#wizard-password-warn-label").getText shouldBe 'empty
    click("#passwordField").`type`("weak")
    find[Label]("#wizard-password-warn-label").getText should include ("weak")
    instance.canContinue.value shouldBe false
  }

  it should "not warn the user about when password is strong" in new Fixture {
    find[Label]("#wizard-password-warn-label").getText shouldBe 'empty
    click("#passwordField").`type`("ThisIsASuperStrongPassw0rd")
    find[Label]("#wizard-password-warn-label").getText shouldBe 'empty
    instance.canContinue.value shouldBe false
  }

  it should "warn the user about mismatching passwords" in new Fixture {
    find[Label]("#wizard-password-warn-label").getText shouldBe 'empty
    click("#passwordField").`type`("CorrectPassword")
    click("#repeatPasswordField").`type`("TypoPassword")
    find[Label]("#wizard-password-warn-label").getText should include ("don't match")
    instance.canContinue.value shouldBe false
  }

  it should "let the user continue is passwords are OK" in new Fixture {
    find[Label]("#wizard-password-warn-label").getText shouldBe 'empty
    click("#passwordField").`type`("CorrectPassword")
    click("#repeatPasswordField").`type`("CorrectPassword")
    find[Label]("#wizard-password-warn-label").getText shouldBe 'empty
    instance.canContinue.value shouldBe true
  }
}
