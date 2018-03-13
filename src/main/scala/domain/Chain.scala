package domain

import akka.util.ByteString
import crypto.SHA3


case class Transaction(amount: BigInt,
                       recipient: Address,
                       txNumber:BigInt,
                       signature: Signature)

case class Block(parentHash: ByteString,
                 transactions: List[Transaction],
                 miner: Address,
                 nonce: ByteString,
                 powHash: ByteString) {

  lazy val hash: ByteString = {
    val txsHash = SHA3.calculate(transactions)
    SHA3.calculate(Seq(parentHash, txsHash, miner.asBytes))
  }

}

case class Signature(r: BigInt, s: BigInt, v: BigInt)

case class PubKey(x: BigInt, y: BigInt)

case class PrvKey(n: BigInt)

case class Address(key: PubKey) {
  lazy val asBytes: ByteString = SHA3.calculate(Seq(ByteString(key.x), ByteString(key.y)))

  override def toString: String = asBytes.utf8String
}
