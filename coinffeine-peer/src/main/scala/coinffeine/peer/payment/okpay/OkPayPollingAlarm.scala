package coinffeine.peer.payment.okpay

import coinffeine.alarms.{Alarm, Severity}

case object OkPayPollingAlarm extends Alarm {

  override def summary = "Cannot poll OKPay balance"

  override def severity = Severity.Normal

  override def whatHappened =
    """
      |There was an error while retrieving your balance from OKPay web services.
      |The service might be unavailable, your user credentials might be invalid, or just
      |the network connectivity is lost.
    """.stripMargin

  override def howToFix =
    """
      |Check your OKPay credentials (wallet ID & seed token) and your network connectivity.
    """.stripMargin
}
