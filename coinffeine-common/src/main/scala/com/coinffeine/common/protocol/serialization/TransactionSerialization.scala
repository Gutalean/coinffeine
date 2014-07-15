package com.coinffeine.common.protocol.serialization

import com.google.protobuf.ByteString

import coinffeine.model.bitcoin._

private[serialization] class TransactionSerialization(network: Network) {

  def deserializePublicKey(byteString: ByteString): PublicKey =
    new PublicKey(TransactionSerialization.NoPrivateKey, byteString.toByteArray)

  def deserializeSignature(byteString: ByteString): TransactionSignature =
    TransactionSignature.decode(byteString.toByteArray, TransactionSerialization.RequireCanonical)

  def deserializeTransaction(byteString: ByteString): ImmutableTransaction =
    new ImmutableTransaction(byteString.toByteArray, network)

  def serialize(key: PublicKey): ByteString = ByteString.copyFrom(key.getPubKey)

  def serialize(sig: TransactionSignature): ByteString = ByteString.copyFrom(sig.encodeToBitcoin())

  def serialize(tx: ImmutableTransaction): ByteString =
    ByteString.copyFrom(tx.get.bitcoinSerialize())
}

private object TransactionSerialization {
  /** Reject deserialization of non-canonical signatures */
  private val RequireCanonical = true

  private val NoPrivateKey = null
}
