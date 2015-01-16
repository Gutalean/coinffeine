package coinffeine.overlay

/** This package implements a [[coinffeine.overlay.OverlayNetwork]] that relies on a relay node
  * that is publicly accessible through Internet.
  *
  * It is implemented as a simple TCP protocol in which the nodes of the overlay network take part
  * as clients and the relay node shuffles the traffic between them.
  *
  * Clients should maintain their TCP connections open in order to keep connected to the overlay
  * network.
  *
  * Protocol messages
  * -----------------
  *
  * The messages (par of the sealed trait [[relay.messages.Message]]) are:
  *
  *  * Join message: just after the TCP channel is open, clients must identify themselves with
  *    their client id.
  *  * Network status message: when the server accepts a client and periodically, this message
  *    informs about the number of nodes in the overlay network.
  *  * Relay message: after the join message clients can send or receive this message
  *    consisting on three fields.
  *
  * Protocol phases
  * ---------------
  *
  *  1. Handshake
  *
  *    1. The client open a TCP connection with the server.
  *    2. The client sends a join message in less than a configurable timeout.
  *    3. The server sends an initial network status message to acknowledge the join in less than
  *       a configurable timeout.
  *
  *  2. Relaying
  *
  *    * At any moment, the client sends a relay message to send a message to other peer.
  *    * At any moment, the server sends a relay message in behalf of any other peer.
  *
  *  3. Closing. Both the server and the client can finish the session by closing the connection.
  *     This is the expected behavior in the case of a timeout happening or when invalid data is
  *     received.
  *
  * Wire protocol
  * -------------
  *
  * At the wire level, there is a frame format implemented by [[relay.messages.Frame]] that is
  * intended just for message delimitation.  The relay protocol commands are serialized by means of
  * protocol buffers and the wrapped in this frames.
  *
  * The frame layout is as follows:
  *
  *      +---+------------+--------------------------+
  *      | M | L  L  L  L |      ... Payload ...     |
  *      +---+------------+--------------------------+
  *
  * Where:
  *
  *  * _M_ is a magic number of one byte.
  *  * _LLLL_ is the length of the payload as 4 bytes integer in big endian order.
  *  * _Payload_ is the serialized protobuf.
  *
  * See the details about the protobuf at `src/main/protobuf/relay.proto` or lookup the generated
  * class [[coinffeine.overlay.relay.protobuf.RelayProtobuf]].
  */
package object relay
