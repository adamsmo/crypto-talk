package domain

import akka.util.ByteString
import crypto.SHA3
import org.bouncycastle.util.encoders.Hex

case class Transaction(
    amount: BigInt,
    txFee: BigInt,
    recipient: Address,
    txNumber: BigInt,
    signature: Signature)

case class Account(txNumber: BigInt, balance: BigInt)

case class UnminedBlock(
    blockNumber: BigInt,
    parentHash: ByteString,
    transactions: List[Transaction],
    miner: Address,
    blockDifficulty: BigInt,
    totalDifficulty: BigInt) {

  lazy val hash: ByteString = {
    val txsHash = SHA3.calculate(transactions)
    SHA3.calculate(Seq(
      ByteString(blockNumber),
      parentHash,
      txsHash,
      miner.asBytes,
      ByteString(blockDifficulty),
      ByteString(totalDifficulty)))
  }
}

case class Block(
    blockNumber: BigInt,
    parentHash: ByteString,
    transactions: List[Transaction],
    miner: Address,
    blockDifficulty: BigInt,
    totalDifficulty: BigInt,
    nonce: ByteString,
    powHash: ByteString) {

  lazy val hash: ByteString = {
    val txsHash = SHA3.calculate(transactions)
    SHA3.calculate(Seq(
      ByteString(blockNumber),
      parentHash,
      txsHash,
      miner.asBytes,
      nonce,
      powHash,
      ByteString(blockDifficulty),
      ByteString(totalDifficulty)))
  }

}

case object Block {
  def apply(b: UnminedBlock, powHash: ByteString, nonce: ByteString): Block =
    Block(
      b.blockNumber,
      b.parentHash,
      b.transactions,
      b.miner,
      b.blockDifficulty,
      b.totalDifficulty,
      nonce,
      powHash)
}

//remark signature contans V that allow to recover public key from r and s
//but for simplicity I decided to just add pub key
case class Signature(r: BigInt, s: BigInt, pubKey: PubKey)

case class PubKey(x: BigInt, y: BigInt)

case class PrvKey(d: BigInt)

case class Address(key: PubKey) {
  lazy val asBytes: ByteString = SHA3.calculate(Seq(ByteString(key.x), ByteString(key.y)))

  override def toString: String = "0x" + Hex.toHexString(asBytes.toArray[Byte])
}
