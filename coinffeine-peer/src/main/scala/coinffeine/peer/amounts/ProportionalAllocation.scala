package coinffeine.peer.amounts

object ProportionalAllocation {

  /** Distribute an amount proportionally to a vector of weights */
  def allocate(amount: BigInt, weights: Vector[BigInt]): Vector[BigInt] = {
    require(amount > 0 && weights.forall(_ > 0), "Amounts should be positive")
    require(weights.nonEmpty, "At least a weight is required")
    val totalWeight = weights.sum

    val (directAllocation, remainders) = weights.map { weight =>
      amount * weight /% totalWeight
    }.unzip

    val roundUps = (amount - directAllocation.sum).toInt
    val roundedRemainders = roundRemainders(roundUps, remainders)
    directAllocation.zip(roundedRemainders).map {  case (elem, round) => elem + round }
  }

  private def roundRemainders(roundUps: Int, remainders: Vector[BigInt]): Vector[BigInt] = {
    val sortedRemainders = remainders.zipWithIndex.sortBy(-_._1)
    val roundedIndexes = sortedRemainders.take(roundUps).map(_._2).toSet
    Vector.tabulate(remainders.size) { index => if (roundedIndexes.contains(index)) 1 else 0 }
  }
}
