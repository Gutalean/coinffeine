package coinffeine.peer.market.orders.archive.h2

import scala.language.implicitConversions
import scala.util.parsing.combinator.JavaTokenParsers

import coinffeine.model.Both
import coinffeine.model.bitcoin.{Hash, PublicKey}
import coinffeine.model.exchange.Exchange.PeerInfo
import coinffeine.model.exchange._

object ExchangeStatusParser {

  private object Parser extends JavaTokenParsers {

    implicit class ObjectPimp[T <: Product with Singleton](val obj: T) extends AnyVal {
      def parser: Parser[T] = obj.productPrefix ^^ { _ => obj }
    }

    val quotedString: Parser[String] = stringLiteral ^^ unquote

    val booleanLiteral: Parser[Boolean] =
      "false" ^^ { _ => false } |
      "true" ^^ { _ => true }

    val peerInfo: Parser[PeerInfo] =
      ("PeerInfo" ~> "(" ~> quotedString ~ "," ~ quotedString <~ ")" ) ^^ {
        case accountId ~ _ ~ encodedKey =>
          val key = PublicKey(org.bitcoinj.core.Utils.HEX.decode(encodedKey))
          PeerInfo(accountId, key)
      }

    val waitingForDeposits: Parser[ExchangeStatus.WaitingDepositConfirmation] =
      ("WaitingDepositConfirmation" ~> "(" ~> peerInfo ~ "," ~ peerInfo <~ ")") ^^ {
        case userInfo ~ _ ~ counterpartInfo =>
          ExchangeStatus.WaitingDepositConfirmation(userInfo, counterpartInfo)
      }

    val exchangingStatus: Parser[ExchangeStatus.Exchanging] =
      ("Exchanging" ~> "(" ~> quotedString ~ "," ~ quotedString <~ ")") ^^ {
        case buyerHash ~ _ ~ sellerHash =>
          ExchangeStatus.Exchanging(Both(new Hash(buyerHash), new Hash(sellerHash)))
      }

    val handshakeFailureCause = HandshakeFailureCause.BrokerAbortion.parser |
      HandshakeFailureCause.CannotCreateDeposits.parser |
      HandshakeFailureCause.SignatureTimeout.parser

    val handshakeFailed: Parser[CancellationCause.HandshakeFailed] =
      ("HandshakeFailed" ~> "(" ~> handshakeFailureCause <~ ")") ^^
        CancellationCause.HandshakeFailed.apply

    val cancellationCause: Parser[CancellationCause] =
      CancellationCause.UserCancellation.parser |
        CancellationCause.CannotStartHandshake.parser |
        handshakeFailed

    val cancellation: Parser[FailureCause.Cancellation] =
      ("Cancellation" ~> "(" ~> cancellationCause <~ ")") ^^ FailureCause.Cancellation.apply

    val invalidCommitments: Parser[AbortionCause.InvalidCommitments] =
      ("InvalidCommitments" ~> "(" ~> booleanLiteral ~ "," ~ booleanLiteral <~ ")") ^^ {
        case invalidBuyer ~ _ ~ invalidSeller =>
          AbortionCause.InvalidCommitments(Both(invalidBuyer, invalidSeller))
      }

    val abortionCause: Parser[AbortionCause] =
      AbortionCause.HandshakeCommitmentsFailure.parser | invalidCommitments

    val abortionParser: Parser[FailureCause.Abortion] =
      ("Abortion" ~> "(" ~> abortionCause <~ ")") ^^ FailureCause.Abortion.apply

    val stepFailed: Parser[FailureCause.StepFailed] =
      ("StepFailed" ~> "(" ~> "\\d{1,5}".r <~ ")") ^^ { step =>
        FailureCause.StepFailed(step.toInt)
      }

    val failureCause: Parser[FailureCause] = cancellation |
      FailureCause.PanicBlockReached.parser |
      abortionParser |
      FailureCause.UnexpectedBroadcast.parser |
      FailureCause.NoBroadcast.parser |
      stepFailed

    val failed: Parser[ExchangeStatus.Failed] =
      ("Failed" ~> "(" ~> failureCause <~ ")") ^^ ExchangeStatus.Failed.apply

    val aborting: Parser[ExchangeStatus.Aborting] =
      ("Aborting" ~> "(" ~> abortionCause <~ ")") ^^ ExchangeStatus.Aborting.apply

    val exchangeStatusParser: Parser[ExchangeStatus] = ExchangeStatus.Handshaking.parser |
      waitingForDeposits |
      exchangingStatus |
      ExchangeStatus.Successful.parser |
      failed |
      aborting

    private def unquote(quotedString: String): String =
      quotedString.substring(1, quotedString.length - 1)
  }

  def parse(input: String): Option[ExchangeStatus] =
    Parser.parseAll(Parser.exchangeStatusParser, input)
      .map(Some.apply)
      .getOrElse(None)
}
