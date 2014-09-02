package coinffeine.peer.payment.okpay

import java.net.URL
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.gargoylesoftware.htmlunit._
import com.gargoylesoftware.htmlunit.html._
import org.eclipse.jetty.util.ajax.JSON

class OkPayProfileExtractor(username: String, password: String) {

  import OkPayProfileExtractor._

  private val client = {
    val result = new WebClient(BrowserVersion.INTERNET_EXPLORER_11)
    result.getOptions.setUseInsecureSSL(true)
    result
  }

  def configureProfile(): Future[Option[OkPayProfile]] = Future {
    login()
    configureBusinessMode()
    lookupWalletsIds().headOption.map { walletId =>
      enableAPI(walletId)
      val token = configureSeedToken(walletId)
      OkPayProfile(token, walletId)
    }
  }

  private[okpay] def login(): Unit = {
    val loginPage: HtmlPage = client.getPage("https://www.okpay.com/es/account/login.html")
    val loginForm: HtmlForm = loginPage.getHtmlElementById("aspnetForm")
    loginForm.getInputByName("ctl00$MainContent$txtLogin").asInstanceOf[HtmlInput]
      .setValueAttribute(username)
    loginForm.getInputByName[HtmlInput]("ctl00$MainContent$txtPassword")
      .setValueAttribute(password)
    val dashboardPage = loginForm.getInputByName("ctl00$MainContent$btnLogin")
      .asInstanceOf[HtmlInput].click().asInstanceOf[HtmlPage]
    dashboardPage.asText()
    Option(dashboardPage.getFirstByXPath("//div[@id='activity']")).getOrElse {
      throw new LoginException(s"Login failed, the page returned was not the Dashboard: " +
        s"${dashboardPage.asText}")
    }
  }

  private[okpay] def lookupWalletsIds(): Seq[String] = {
    val walletsPage = retrievePage("wallets.html")
    for {
      table <- walletsPage.getBody.getHtmlElementsByTagName[HtmlTable]("table").headOption.toSeq
      row <- table.getRows
      cell <- row.getCells if cell.asText().startsWith("OK")
    } yield cell.asText()
  }

  private def retrievePage(relativePath: String) =
    client.getPage[HtmlPage](s"https://www.okpay.com/es/account/profile/$relativePath")

  private[okpay] def configureBusinessMode(): Unit = {
    val accountTypePage = retrievePage("general/account-type.html")
    val accountTypeForm  = accountTypePage.getHtmlElementById[HtmlForm]("aspnetForm")
    val radioButtonMerchant = accountTypeForm.
      getRadioButtonsByName("ctl00$ctl00$MainContent$MainContent$client")(1)
      .asInstanceOf[HtmlRadioButtonInput]
    if(!radioButtonMerchant.isChecked) {
      radioButtonMerchant.setChecked(true)
      accountTypeForm.getInputByName[HtmlInput](
        "ctl00$ctl00$MainContent$MainContent$Button_Continue").click()
    }
  }

  private[okpay] def enableAPI(walletId: String): Unit = {
    val settingsPage = retrievePage(s"wallet/$walletId")
    val settingsForm: HtmlForm = settingsPage.getHtmlElementById("aspnetForm")
    val checkButtonApi = settingsForm
      .getInputByName[HtmlCheckBoxInput](
        "ctl00$ctl00$MainContent$MainContent$Integration_Settings_cbxEnableAPI")
    checkButtonApi.setChecked(true)
    val saveButton = settingsForm
      .getInputByName[HtmlSubmitInput](
        "ctl00$ctl00$MainContent$MainContent$IntegrationSettings_btnSave")
    saveButton.click()
  }

  private[okpay] def configureSeedToken(walletId: String): String = {
    val settingsPage = retrievePage("wallet/" + walletId)
    val settingsForm: HtmlForm = settingsPage.getHtmlElementById("aspnetForm")
    val token = generateSeedToken()
    settingsForm.getInputByName[HtmlHiddenInput](
      "ctl00$ctl00$MainContent$MainContent$hidEnrPass").setValueAttribute(token)

    val saveButton = settingsForm
      .getInputByName[HtmlSubmitInput](
        "ctl00$ctl00$MainContent$MainContent$IntegrationSettings_btnSave")
    saveButton.click()
    token
  }

  private[okpay] def generateSeedToken(): String = {
    val request = new WebRequest(
      new URL(
        "https://www.okpay.com/WebService/OkPayWebService.asmx/GetApiPassword"),
      HttpMethod.POST)
    request.setAdditionalHeader("Content-Type", "application/json")

    val tokenResponse = client.getPage(request)
      .asInstanceOf[Page].getWebResponse.getContentAsString
    val tokenResponseMap = JSON.parse(tokenResponse).asInstanceOf[java.util.Map[String, String]]
    tokenResponseMap.get("d")
  }
}

object OkPayProfileExtractor {

  val SeedTokenLength = 25

  case class OkPayProfile(token: String, walletId: String) {
    require(token.length == SeedTokenLength,
      s"Received a token which size is not $SeedTokenLength: $token (size ${token.length}})")
  }

  case class LoginException(message: String, cause: Throwable = null)
    extends Exception(message, cause)
}
