package coinffeine.peer.payment.okpay.profile

import java.net.URL
import scala.collection.JavaConversions._

import com.gargoylesoftware.htmlunit._
import com.gargoylesoftware.htmlunit.html._
import org.eclipse.jetty.util.ajax.JSON

class ScrappingProfile private (val client: WebClient) {
  import ScrappingProfile._

  def accountMode: AccountMode = {
    val profilePage = retrieveProfilePage("general/account-type.html")
    val radioButtonMerchant =
      profilePage.getElementById[HtmlRadioButtonInput]("cbxMerchant", false)
    if(radioButtonMerchant.isChecked) AccountMode.Business else AccountMode.Client
  }

  def accountMode_=(newAccountMode: AccountMode): Unit = {
    ProfileException.wrap(s"Cannot configure $newAccountMode") {
      val profilePage = retrieveProfilePage("general/account-type.html")
      val accountTypeForm = getForm(profilePage)
      val radioButtonMerchant = profilePage.getElementById[HtmlRadioButtonInput](
        AccountModeButtons(newAccountMode), false)
      radioButtonMerchant.setChecked(true)
      submitForm(accountTypeForm, "ctl00$ctl00$MainContent$MainContent$Button_Continue")
    }
  }

  def walletId(): String = lookupWalletsIds().headOption.getOrElse {
    throw new ProfileException("No wallet was found")
  }

  def enableAPI(walletId: String): Unit = {
    ProfileException.wrap(s"Cannot enable API for wallet $walletId") {
      val settingsPage = retrieveProfilePage(s"wallet/$walletId")
      val settingsForm = getForm(settingsPage)
      val checkButtonApi = settingsForm
        .getInputByName[HtmlCheckBoxInput](
          "ctl00$ctl00$MainContent$MainContent$Integration_Settings_cbxEnableAPI")
      checkButtonApi.setChecked(true)
      submitForm(settingsForm, "ctl00$ctl00$MainContent$MainContent$IntegrationSettings_btnSave")
    }
  }

  private def lookupWalletsIds(): Seq[String] = {
    val walletsPage = retrieveProfilePage("wallets.html")
    for {
      table <- walletsPage.getBody.getHtmlElementsByTagName[HtmlTable]("table").headOption.toSeq
      row <- table.getRows
      cell <- row.getCells
      cellContent = cell.asText
      if cellContent.startsWith("OK")
    } yield cellContent
  }

  def configureSeedToken(walletId: String): String = {
    val settingsPage = retrieveProfilePage("wallet/" + walletId)
    val settingsForm = getForm(settingsPage)
    val token = generateSeedToken(settingsForm)
    require(token.length == SeedTokenLength,
      s"Received a token which size is not $SeedTokenLength: $token (size ${token.length}})")
    fillInField(settingsForm, "ctl00$ctl00$MainContent$MainContent$hidEnrPass", token)
    submitForm(settingsForm, "ctl00$ctl00$MainContent$MainContent$IntegrationSettings_btnSave")
    token
  }

  private def generateSeedToken(settingsForm: HtmlForm): String =
    ProfileException.wrap("Cannot generate a seed token") {
      val request = new WebRequest(
        new URL(s"$OkPayBaseUrl/WebService/OkPayWebService.asmx/GetApiPassword"),
        HttpMethod.POST)
      request.setAdditionalHeader("Content-Type", "application/json")
      request.setAdditionalHeader("__AntiXsrfToken",
        getField(settingsForm, "ctl00$ctl00$__AntiXsrfToken").getValueAttribute)

      val tokenResponse = client.getPage[Page](request).getWebResponse.getContentAsString
      val tokenResponseMap = JSON.parse(tokenResponse).asInstanceOf[java.util.Map[String, String]]
      tokenResponseMap("d")
    }

  private def login(username: String, password: String): Unit = {
    val loginForm = getForm(retrievePage(s"$OkPayBaseUrl/es/account/login.html"))
    fillInField(loginForm, "ctl00$MainContent$txtLogin", username)
    fillInField(loginForm, "ctl00$MainContent$txtPassword", password)
    val dashboardPage = submitForm(loginForm, "ctl00$MainContent$btnLogin")
    ProfileException.wrap(dashboardPage, "Login failed, the page returned is not the dashboard") {
      require(dashboardPage.getFirstByXPath("//div[@id='activity']") != null)
    }
  }

  private def retrievePage(url: String): HtmlPage = ProfileException.wrap(s"Cannot load $url") {
    client.getPage[HtmlPage](url)
  }

  private def retrieveProfilePage(relativePath: String): HtmlPage =
    retrievePage(s"$OkPayBaseUrl/es/account/profile/$relativePath")

  private def getForm(page: HtmlPage): HtmlForm =
    ProfileException.wrap(s"No form found in page content: ${page.asText()}") {
      page.getHtmlElementById[HtmlForm]("aspnetForm")
    }

  private def fillInField(form: HtmlForm, fieldName: String, value: String): Unit = {
    val field = getField(form, fieldName)
    ProfileException.wrap(
      form, s"Cannot set form field $fieldName at ${form.getHtmlPageOrNull.getUrl}") {
      field.setValueAttribute(value)
    }
  }

  private def getField(form: HtmlForm, fieldName: String): HtmlInput =
    ProfileException.wrap(form, s"Field $fieldName not found in ${form.getHtmlPageOrNull.getUrl}") {
      form.getInputByName[HtmlInput](fieldName)
    }

  private def submitForm(form: HtmlForm, submitButton: String): HtmlPage =
    ProfileException.wrap(
      form, s"Cannot submit form with button $submitButton at ${form.getHtmlPageOrNull.getUrl}") {
      form.getInputByName[HtmlSubmitInput](submitButton).click[HtmlPage]()
    }
}

object ScrappingProfile {
  private val OkPayBaseUrl = "https://www.okpay.com"
  private val SeedTokenLength = 25
  private val AccountModeButtons = Map[AccountMode, String](
    AccountMode.Business -> "cbxMerchant",
    AccountMode.Client -> "cbxBuyer"
  )

  def login(username: String, password: String): ScrappingProfile = {
    val profile = new ScrappingProfile(createClient)
    profile.login(username, password)
    profile
  }

  private def createClient: WebClient = {
    val result = new WebClient(BrowserVersion.INTERNET_EXPLORER_11)
    result.getOptions.setUseInsecureSSL(true)
    result
  }
}
