package coinffeine.model.currency

import java.util.{Currency => JavaCurrency}

case object Euro extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("EUR")
}

case object UsDollar extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("USD")
}

case object PoundSterling extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("GBP")
}

case object HongKongDollar extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("HKD")
}

case object SwissFranc extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("CHF")
}

case object AustralianDollar extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("AUD")
}

case object PolishZloty extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("PLN")
}

case object Yen extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("JPY")
}

case object SwedishKrona extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("SEK")
}

case object DenmarkKroner extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("DKK")
}

case object CanadianDollar extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("CAD")
}

case object RussianRouble extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("RUB")
}

case object Koruny extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("CZK")
}

case object CroatianKuna extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("HRK")
}

case object HungarianForint extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("HUF")
}

case object NorwegianKrone extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("NOK")
}

case object NewZealandDollar extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("NZD")
}

case object RomanianNewLeu extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("RON")
}

case object TurkishLira extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("TRY")
}

case object SouthAfricanRand extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("ZAR")
}

case object PhilippinePeso extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("PHP")
}

case object SingaporeDollar extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("SGD")
}

case object MalaysianRinggit extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("MYR")
}

case object TaiwanNewDollar extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("TWD")
}

case object IsraeliNewSheqel extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("ILS")
}

case object MexicanPeso extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("MXN")
}

case object YuanRenminbi extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("CNY")
}

case object NigerianNairas extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("NGN")
}

