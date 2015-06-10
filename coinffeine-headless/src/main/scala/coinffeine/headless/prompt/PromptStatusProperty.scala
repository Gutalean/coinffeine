package coinffeine.headless.prompt

import scala.concurrent.ExecutionContext

import coinffeine.common.properties.{Cancellable, MutableProperty, Property}
import coinffeine.peer.api.CoinffeineApp

class PromptStatusProperty(app: CoinffeineApp)
                          (implicit ec: ExecutionContext) extends Property[PromptStatus] {

  private val mutableProperty = {
    val property = new MutableProperty[PromptStatus](PromptStatus(
      knownBroker = app.network.brokerId.get.isDefined,
      coinffeinePeers = app.network.activePeers.get,
      bitcoinPeers = app.bitcoinNetwork.activePeers.get,
      blockchainStatus = app.bitcoinNetwork.blockchainStatus.get
    ))
    app.network.brokerId.onNewValue { brokerOpt =>
      property.update(_.copy(knownBroker = brokerOpt.isDefined))
    }
    app.network.activePeers.onNewValue { peers =>
      property.update(_.copy(coinffeinePeers = peers))
    }
    app.bitcoinNetwork.activePeers.onNewValue { peers =>
      property.update(_.copy(bitcoinPeers = peers))
    }
    app.bitcoinNetwork.blockchainStatus.onNewValue { blockchainStatus =>
      property.update(_.copy(blockchainStatus = blockchainStatus))
    }
    property
  }

  override def get: PromptStatus = mutableProperty.get

  override def onChange(handler: OnChangeHandler)(implicit executor: ExecutionContext): Cancellable =
    mutableProperty.onChange(handler)(executor)
}
