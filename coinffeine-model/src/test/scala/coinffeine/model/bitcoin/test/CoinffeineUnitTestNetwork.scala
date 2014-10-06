package coinffeine.model.bitcoin.test

import org.bitcoinj.params.UnitTestParams

import coinffeine.model.bitcoin.NetworkComponent

object CoinffeineUnitTestNetwork extends UnitTestParams {
  // Ensures difficulty stays at minimum level
  interval = Int.MaxValue

  // Ensures that bitcoins are spendable as soon as they are mined
  spendableCoinbaseDepth = 0

  // Ensures that the miner's reward for each block is constant
  subsidyDecreaseBlockCount = Int.MaxValue

  trait Component extends NetworkComponent {
    override val network = CoinffeineUnitTestNetwork
    override val peerAddresses = Seq.empty
  }
}
