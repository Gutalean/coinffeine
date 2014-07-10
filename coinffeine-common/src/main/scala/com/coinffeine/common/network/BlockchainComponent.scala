package com.coinffeine.common.network

import com.google.bitcoin.core.AbstractBlockChain

trait BlockchainComponent {

  def blockchain: AbstractBlockChain
}
