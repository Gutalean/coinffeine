package coinffeine.headless.prompt

import coinffeine.model.bitcoin.BlockchainStatus

case class PromptStatus(knownBroker: Boolean,
                        coinffeinePeers: Int,
                        bitcoinPeers: Int,
                        blockchainStatus: BlockchainStatus)
