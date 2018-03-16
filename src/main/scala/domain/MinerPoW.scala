package domain

import akka.util.ByteString
import crypto.SHA3

import scala.util.Random

object MinerPoW {

  /**
   * PoW function based on hash leading zeros
   * @param powBlockHash hash for block calculated for mining
   * @param difficulty number of leading bits that have to be zeros
   * @return mined PoW with appropriate nonce
   */
  def mineBlock(powBlockHash: ByteString, difficulty: Long): (ByteString, ByteString) = {
    val nonce = ByteString(Array.fill(20)(Random.nextInt(256).toByte))

    val powHash = SHA3.calculate(Seq(powBlockHash, nonce))
    val formated = format(powHash)

    val expectedPrefix = "0" * difficulty.toInt

    if (formated.startsWith(expectedPrefix)) {
      (powHash, nonce)
    } else {
      mineBlock(powBlockHash, difficulty)
    }
  }

  /**
   * Converts ByteString to string of bits
   */
  def format(in: ByteString): String = {
    in.map { b =>
      val binary = Integer.toBinaryString(b)
      val padded = ("0" * 8) + binary
      padded.takeRight(8)
    }.mkString("")
  }
}
