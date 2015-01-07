package coinffeine.overlay

/** This package implements a [[coinffeine.overlay.OverlayNetwork]] that relies on a relay node
  * that is publicly accessible through Internet.
  *
  * It is implemented as a simple TCP protocol in which the nodes of the overlay network take part
  * as clients and the relay node shuffles the traffic between them.
  *
  * Protocol messages:
  *
  *  * Join message: just after the TCP channel is open, clients must identify themselves with
  *    their client id.
  *  * Network status message: when the server accepts a client and periodically, this message
  *    informs about the number of nodes in the overlay network.
  *  * Relay message: after the join message clients can send or receive this message
  *    consisting on three fields:
  *
  *      * Overlay id: 20 bytes indicating the communicating counterpart.
  *      * Payload size: an integer (4 bytes) indicating the size of the payload.
  *      * Payload: message to be delivered.
  *
  * Clients should maintain their TCP connections open in order to keep connected to the overlay
  * network.
  */
package object relay
