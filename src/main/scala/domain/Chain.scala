package domain

import java.awt.print.Book

import actors.CoinNode
import akka.util.ByteString
import crypto.{ ECDSA, SHA3 }
import org.bouncycastle.util.encoders.Hex

case class Transaction(
    amount: BigInt,
    txFee: BigInt,
    recipient: Address,
    txNumber: BigInt,
    signature: Signature) {

  lazy val sender: Option[Address] = {
    ECDSA.verify(signature, SHA3.calculate(this))
  }

}

case class Account(txNumber: BigInt, balance: BigInt) {
  def subtract(amount: BigInt) = copy(balance - amount)
  def add(amount: BigInt) = copy(balance + amount)
}

object Account {
  def empty = Account(0, 0)
}

trait Block {
  val blockNumber: BigInt
  val parentHash: ByteString
  val transactions: List[Transaction]
  val miner: Address
  val blockDifficulty: BigInt
  val totalDifficulty: BigInt
}

case class UnminedBlock(
    blockNumber: BigInt,
    parentHash: ByteString,
    transactions: List[Transaction],
    miner: Address,
    blockDifficulty: BigInt,
    totalDifficulty: BigInt) extends Block {

  lazy val hash = Block.hashForMining(this)
}

case class MinedBlock(
    blockNumber: BigInt,
    parentHash: ByteString,
    transactions: List[Transaction],
    miner: Address,
    blockDifficulty: BigInt,
    totalDifficulty: BigInt,
    nonce: ByteString,
    powHash: ByteString) extends Block {

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

object MinedBlock {
  def apply(b: UnminedBlock, powHash: ByteString, nonce: ByteString): MinedBlock =
    MinedBlock(
      b.blockNumber,
      b.parentHash,
      b.transactions,
      b.miner,
      b.blockDifficulty,
      b.totalDifficulty,
      nonce,
      powHash)
}

object Block {
  def hashForMining(block: Block): ByteString = {
    val txsHash = SHA3.calculate(block.transactions)
    SHA3.calculate(Seq(
      ByteString(block.blockNumber),
      block.parentHash,
      txsHash,
      block.miner.asBytes,
      ByteString(block.blockDifficulty),
      ByteString(block.totalDifficulty)))
  }
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
