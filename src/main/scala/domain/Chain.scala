package domain

import akka.util.ByteString
import crypto.SHA3


case class Transaction(amount: BigInt,
                       recipient: Address,
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

case class Address() {
  def asBytes: ByteString = ByteString.empty
}
