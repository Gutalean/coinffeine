package coinffeine.peer.payment.okpay.profile

import java.net.URL
import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

import com.gargoylesoftware.htmlunit._
import com.gargoylesoftware.htmlunit.html._
import com.typesafe.scalalogging.LazyLogging
import org.eclipse.jetty.util.ajax.JSON

import coinffeine.model.payment.okpay.VerificationStatus

class ScrappingProfile private (val client: WebClient) extends Profile with LazyLogging {
  import ScrappingProfile._

  override def accountMode: AccountMode = {
    val profilePage = retrieveProfilePage(AccountTypeLocation)
    val radioButtonMerchant = profilePage.getElementById[HtmlRadioButtonInput](
      AccountModeButtons(AccountMode.Business), false)
    if(radioButtonMerchant.isChecked) AccountMode.Business else AccountMode.Client
  }

  override def accountMode_=(newAccountMode: AccountMode): Unit = {
    ProfileException.wrap(s"Cannot configure $newAccountMode") {
      setAccountMode(retrieveProfilePage(AccountTypeLocation), newAccountMode)
    }
  }

  private def setAccountMode(
      accountTypePage: HtmlPage, newAccountMode: AccountMode): HtmlPage = {
    val accountTypeForm = getForm(accountTypePage)
    accountTypePage
      .getElementById[HtmlRadioButtonInput](AccountModeButtons(newAccountMode), false)
      .click[HtmlPage]()
    ProfileException.wrap(accountTypeForm, s"Cannot submit form") {
      accountTypeForm.getInputByName[HtmlSubmitInput](
        "ctl00$ctl00$MainContent$MainContent$Button_Continue").click[HtmlPage]()
    }
  }

  override def walletId(): String = lookupWalletsIds().headOption.getOrElse {
    throw new ProfileException("No wallet was found")
  }

  override def enableAPI(walletId: String): Unit = {
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

  override def configureSeedToken(walletId: String): String = {
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
    val loginForm = getForm(retrievePage(s"$OkPayBaseUrl/en/account/login.html"))
    fillInField(loginForm, "ctl00$MainContent$txtLogin", username)
    fillInField(loginForm, "ctl00$MainContent$txtPassword", password)
    val result = submitForm(loginForm, "ctl00$MainContent$btnLogin")
    ProfileException.wrap(result, "Login failed") {
      require(!result.getUrl.toString.endsWith("account/login.html"))
      logger.info("Logged in and redirected to {}", result.getUrl)
    }
  }

  private def retrievePage(url: String): HtmlPage = ProfileException.wrap(s"Cannot load $url") {
    client.getPage[HtmlPage](url)
  }

  private def retrieveProfilePage(relativePath: String): HtmlPage =
    retrievePage(s"$OkPayBaseUrl/en/account/profile/$relativePath")

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

  override def verificationStatus: VerificationStatus = {
    val welcomePage = retrievePage(s"$OkPayBaseUrl/en/account/index.html")
    val verificationStatusName =
      welcomePage.getAnchorByHref("/en/account/profile/verification.html").getTextContent.trim
    VerificationStatusNames.get(verificationStatusName).getOrElse(
      throw new ProfileException(s"Verification status not found. '$verificationStatusName' was found.")
    )
  }
}

object ScrappingProfile {
  private val OkPayBaseUrl = "https://www.okpay.com"
  private val SeedTokenLength = 25
  private val AccountModeButtons = Map[AccountMode, String](
    AccountMode.Business -> "cbxMerchant",
    AccountMode.Client -> "cbxBuyer"
  )
  private val AccountTypeLocation = "general/account-type.html"
  private val VerificationStatusNames = Map[String, VerificationStatus](
    "Verified" -> VerificationStatus.Verified,
    "Not verified" -> VerificationStatus.NotVerified
  )

  def login(username: String, password: String)
           (implicit context: ExecutionContext): Future[ScrappingProfile] = Future {
    val profile = new ScrappingProfile(createClient)
    profile.login(username, password)
    profile
  }

  private def createClient: WebClient = {
    val result = new WebClient(BrowserVersion.INTERNET_EXPLORER_11)
    result.getOptions.setUseInsecureSSL(true)
    result.getOptions.setCssEnabled(false)
    result
  }
}
