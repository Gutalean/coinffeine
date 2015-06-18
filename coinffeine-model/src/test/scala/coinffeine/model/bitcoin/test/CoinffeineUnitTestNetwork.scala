package coinffeine.model.bitcoin.test

import org.bitcoinj.params.UnitTestParams

import coinffeine.model.bitcoin.NetworkComponent

case object CoinffeineUnitTestNetwork extends UnitTestParams with NetworkComponent.SeedPeers {
  // Ensures difficulty stays at minimum level
  interval = Int.MaxValue

  // Ensures that bitcoins are spendable as soon as they are mined
  spendableCoinbaseDepth = 0

  // Ensures that the miner's reward for each block is constant
  subsidyDecreaseBlockCount = Int.MaxValue

  // No need of seed peers as we don't communicate with the external world
  override val seedPeers = Seq.empty

  def setSpendableCoinbaseDepth(depth: Int): Unit = {
    require(depth >= 0, "Spendable coinbase depth must not be negative")
    spendableCoinbaseDepth = depth
  }

  trait Component extends NetworkComponent {
    override val network = CoinffeineUnitTestNetwork
  }
}
