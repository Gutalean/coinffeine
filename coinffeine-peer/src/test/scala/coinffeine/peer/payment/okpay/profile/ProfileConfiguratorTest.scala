package coinffeine.peer.payment.okpay.profile

import coinffeine.common.test.{FutureMatchers, UnitTest}
import coinffeine.model.payment.okpay.VerificationStatus
import coinffeine.peer.payment.okpay.OkPayApiCredentials

class ProfileConfiguratorTest extends UnitTest with FutureMatchers {

  "An OKPay profile configurator" should "switch to business mode from other modes" in
    new Fixture {
      configurator.configure().futureValue
      profile.accountMode shouldBe AccountMode.Business
    }

  it should "enable API use" in new Fixture {
    configurator.configure().futureValue
    profile.apiEnabled shouldBe true
  }

  it should "configure and return API credentials" in new Fixture {
    val result = configurator.configure().futureValue
    profile.seedToken should not be 'empty
    result.credentials shouldBe OkPayApiCredentials(profile.walletId, profile.seedToken.get)
  }

  it should "retrieve the verification status" in new Fixture {
    profile.givenVerificationStatus(VerificationStatus.Verified)
    val result = configurator.configure().futureValue
    result.verificationStatus shouldBe VerificationStatus.Verified
  }

  trait Fixture {
    protected val profile = new FakeProfile()
    protected val configurator = new ProfileConfigurator(profile)
  }

  class FakeProfile extends Profile {
    override val walletId = "FakeId001"
    override var accountMode: AccountMode = AccountMode.Client
    var apiEnabled = false
    var seedToken: Option[String] = None
    private var _verificationStatus: VerificationStatus = _

    override def enableAPI(id: String): Unit = {
      require(id == walletId)
      apiEnabled = true
    }

    override def configureSeedToken(id: String) = {
      require(id == walletId)
      seedToken = Some("seedToken")
      seedToken.get
    }

    override def verificationStatus = _verificationStatus

    def givenVerificationStatus(verificationStatus: VerificationStatus): Unit = {
      _verificationStatus = verificationStatus
    }
  }
}
