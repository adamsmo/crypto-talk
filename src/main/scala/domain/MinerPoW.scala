package domain

import akka.util.ByteString
import crypto.SHA3

import scala.annotation.tailrec
import scala.util.Random

object MinerPoW {

  /**
   * PoW function based on hash leading zeros
   *
   * @param hashForMining hash for block calculated for mining
   * @param difficulty number of leading bits that have to be zeros
   * @return mined PoW with appropriate nonce
   */
  //todo 2.3 potentially infinite loop that looks for PoW solution
  @tailrec
  def mineBlock(
    hashForMining: ByteString,
    difficulty: BigInt): (ByteString, ByteString) = {
    val nonce = ByteString(Array.fill(20)(Random.nextInt(256).toByte))
    val powHash = SHA3.calculate(Seq(hashForMining, nonce))

    val expectedPrefix = "0" * difficulty.toInt

    if (format(powHash).startsWith(expectedPrefix)) {
      (powHash, nonce)
    } else {
      mineBlock(hashForMining, difficulty)
    }
  }

  def isValidPoW(block: MinedBlock): Boolean = {
    val expectedPrefix = "0" * block.blockDifficulty.toInt

    val correctHash = SHA3.calculate(
      Seq(Block.hashForMining(block), block.nonce)) == block.powHash
    val difficulty = format(block.powHash).startsWith(expectedPrefix)

    correctHash && difficulty
  }

  /**
   * Converts ByteString to string of bits
   */
  private def format(in: ByteString): String = {
    in.map { b =>
      val binary = Integer.toBinaryString(b)
      val padded = ("0" * 8) + binary
      //take all bits from byte and padded zeros
      padded.takeRight(8)
    }
      .mkString("")
  }
}
