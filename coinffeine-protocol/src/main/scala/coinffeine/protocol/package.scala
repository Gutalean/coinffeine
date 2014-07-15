package coinffeine

/** Module that provides the means to communicate Coinffeine peers.
  *
  * The module is responsible for providing classes representing the messages that can be
  * sent between peers and a message gateway with the logic to communicate them. That comprises:
  *
  *  * Wire protocol description based on Protocol Buffers IDL
  *  * Message representation as nice case classes
  *  * A message gateway, able to send and receive messages to and from the Coinffeine network
  */
package object protocol
