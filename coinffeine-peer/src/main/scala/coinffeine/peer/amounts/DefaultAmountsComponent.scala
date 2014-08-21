package coinffeine.peer.amounts

trait DefaultAmountsComponent extends AmountsComponent {
  override lazy val orderFundsCalculator = new DefaultOrderFundsCalculator
}
