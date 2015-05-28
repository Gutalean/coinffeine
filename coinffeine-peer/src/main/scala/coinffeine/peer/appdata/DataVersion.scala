package coinffeine.peer.appdata

case class DataVersion(value: Int) extends Ordered[DataVersion] {
  require(value > 0, s"Data version must be positive ($value given)")

  override def compare(that: DataVersion) = value.compareTo(that.value)
}

object DataVersion {
  val Current = DataVersion(2)
}
