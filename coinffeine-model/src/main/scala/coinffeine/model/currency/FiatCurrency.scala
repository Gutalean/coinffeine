package coinffeine.model.currency

import java.util.{Currency => JavaCurrency}

trait FiatCurrency extends Currency {
  override type Amount = FiatAmount

  val javaCurrency: JavaCurrency
  override lazy val preferredSymbolPosition = Currency.SymbolSuffixed
  override lazy val symbol = javaCurrency.getCurrencyCode
  override lazy val precision = javaCurrency.getDefaultFractionDigits
  override lazy val toString = symbol

  override def fromUnits(units: Long) = FiatAmount(units, this)

  def sum(addends: Seq[FiatAmount]) = addends.foldLeft(zero)(_ + _)
}

object FiatCurrency {

  def get(currencyCode: String): Option[FiatCurrency] =
    get(JavaCurrency.getInstance(currencyCode))

  def get(javaCurrency: JavaCurrency): Option[FiatCurrency] =
    supported.find(_.javaCurrency == javaCurrency)

  def apply(currencyCode: String): FiatCurrency = apply(JavaCurrency.getInstance(currencyCode))

  def apply(javaCurrency: JavaCurrency): FiatCurrency = get(javaCurrency).getOrElse(
    throw new IllegalArgumentException(
      s"cannot convert $javaCurrency into a known Coinffeine fiat currency"))

  val supported: Set[FiatCurrency] = Set(
    Euro,
    UsDollar,
    PoundSterling,
    HongKongDollar,
    SwissFranc,
    AustralianDollar,
    PolishZloty,
    Yen,
    SwedishKrona,
    DenmarkKroner,
    CanadianDollar,
    RussianRouble,
    Koruny,
    CroatianKuna,
    HungarianForint,
    NorwegianKrone,
    NewZealandDollar,
    RomanianNewLeu,
    TurkishLira,
    SouthAfricanRand,
    PhilippinePeso,
    SingaporeDollar,
    MalaysianRinggit,
    TaiwanNewDollar,
    IsraeliNewSheqel,
    MexicanPeso,
    YuanRenminbi,
    NigerianNairas
  )
}
