package coinffeine.model.bitcoin

import com.google.bitcoin.params.TestNet3Params

trait IntegrationTestNetworkComponent extends NetworkComponent {
  override def network = TestNet3Params.get()
}
