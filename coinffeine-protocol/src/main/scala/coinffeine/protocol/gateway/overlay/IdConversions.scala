package coinffeine.protocol.gateway.overlay

import scala.language.implicitConversions

import coinffeine.model.network._
import coinffeine.overlay.OverlayId

/** Implicit conversions between node and overlay IDs */
trait IdConversions {
  implicit def overlayIdPimps(overlayId: OverlayId): IdConversions.OverlayIdPimps =
    new IdConversions.OverlayIdPimps(overlayId)

  implicit def nodeIdPimps(nodeId: NodeId): IdConversions.NodeIdPimps =
    new IdConversions.NodeIdPimps(nodeId)
}

object IdConversions {

  class OverlayIdPimps(val overlayId: OverlayId) extends AnyVal {
    def toNodeId: NodeId =
      if (overlayId.value == OverlayId.MaxValue) BrokerId
      else PeerId(overlayId.value.toString(16))
  }

  class NodeIdPimps(val nodeId: NodeId) extends AnyVal {
    def toOverlayId: OverlayId = nodeId match {
      case BrokerId => OverlayId(OverlayId.MaxValue)
      case PeerId(value) => OverlayId(BigInt(value, 16))
    }
  }
}
