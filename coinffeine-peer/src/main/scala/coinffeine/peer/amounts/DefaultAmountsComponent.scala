package coinffeine.peer.amounts

trait DefaultAmountsComponent extends AmountsComponent {
  override lazy val exchangeAmountsCalculator = new DefaultExchangeAmountsCalculator
}
