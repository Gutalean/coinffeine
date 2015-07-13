package coinffeine.protocol.gateway.overlay

import org.scalatest.prop.PropertyChecks

import coinffeine.common.test.UnitTest
import coinffeine.model.network.{BrokerId, PeerId}
import coinffeine.overlay.OverlayId

class IdConversionsTest extends UnitTest with PropertyChecks with IdConversions {

  "Node ids" should "be converted to overlay ids" in {
    BrokerId.toOverlayId shouldBe OverlayId(OverlayId.MaxValue)
    PeerId("f" * 40).toOverlayId shouldBe OverlayId(OverlayId.MaxValue)
    PeerId("0" * 40).toOverlayId shouldBe OverlayId(OverlayId.MinValue)
    PeerId("0" * 39 + 1).toOverlayId shouldBe OverlayId(1)
    PeerId("0123456789012345678901234567890123456789").toOverlayId shouldBe
      OverlayId(BigInt("0123456789012345678901234567890123456789", 16))
  }

  "Overlay ids" should "be converted to node ids" in {
    OverlayId(OverlayId.MaxValue).toNodeId shouldBe BrokerId
    OverlayId(OverlayId.MinValue).toNodeId shouldBe PeerId("0" * 40)
    OverlayId(1).toNodeId shouldBe PeerId("0" * 39 + 1)
    OverlayId(BigInt("0123456789012345678901234567890123456789", 16)).toNodeId shouldBe
      PeerId("0123456789012345678901234567890123456789")
  }
}
