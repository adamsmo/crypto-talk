package crypto

import akka.util.ByteString
import domain.{ Signature, Transaction }
import org.bouncycastle.crypto.digests.SHA3Digest

object SHA3 {
  def calculate(sig: Signature): ByteString = {
    calculate(Seq(ByteString(sig.r), ByteString(sig.s), ByteString(sig.pubKey.x), ByteString(sig.pubKey.y)))
  }

  def calculate(tx: Transaction): ByteString = {
    calculate(Seq(ByteString(tx.amount), ByteString(tx.txFee), tx.recipient.asBytes, ByteString(tx.txNumber), calculate(tx.signature)))
  }

  def calculate(txs: List[Transaction]): ByteString = {
    val txHashes = txs.map(t => calculate(t))
    calculate(txHashes)
  }

  def calculate(in: ByteString): ByteString = calculate(Seq(in))

  def calculate(in: Seq[ByteString]): ByteString = {
    val digest = new SHA3Digest
    in.foreach { bytes: ByteString =>
      val array = bytes.toArray[Byte]
      digest.update(array, 0, array.length)
    }

    val out = Array.ofDim[Byte](32)
    digest.doFinal(out, 0)
    ByteString(out)
  }
}
