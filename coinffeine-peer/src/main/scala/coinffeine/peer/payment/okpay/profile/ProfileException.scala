package coinffeine.peer.payment.okpay.profile

import scala.util.control.NonFatal

import com.gargoylesoftware.htmlunit.html.{HtmlDivision, HtmlForm, HtmlPage}

case class ProfileException(message: String, cause: Throwable = null)
  extends Exception(message, cause)

object ProfileException {
  def wrap[T](message: String)(block: => T): T = try {
    block
  } catch {
    case NonFatal(ex) => throw ProfileException(message, ex)
  }

  def wrap[T](form: HtmlForm, message: String)(block: => T): T =
    wrap(s"$message.\nForm content: ${form.asXml()}")(block)

  def wrap[T](page: HtmlPage, message: String)(block: => T): T =
    wrap(s"$message.\nLocation: ${page.getUrl}\n${errorMessageOrFullPage(page)}")(block)

  private def errorMessageOrFullPage(page: HtmlPage): String =
    Option(page.getFirstByXPath[HtmlDivision]("//div[@class='strong-error-block']"))
      .fold(s"Page content: ${page.asText()}") { errorBlock =>
      s"Error block: ${errorBlock.asText()}"
    }
}
