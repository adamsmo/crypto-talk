package presentation

import actors.CoinNode
import actors.CoinNode.NodeParams
import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKit }
import crypto.ECDSA
import domain._
import org.scalatest.concurrent.{ Eventually, PatienceConfiguration }
import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.duration._

class TestSetup extends TestKit(ActorSystem("for-presentation"))
  with FlatSpecLike
  with Matchers
  with ImplicitSender
  with BeforeAndAfterAll
  with Eventually

trait StandardPatience {
  this: PatienceConfiguration =>
  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 1.second)
}

trait Env {
  val log: Logger = LoggerFactory.getLogger(this.getClass)

  val (prv, pub) = ECDSA.generateKeyPair()
  val recipientAddress = Address(pub)

  def generateMinerAddress(): Address = {
    val (prv, pub) = ECDSA.generateKeyPair()
    Address(pub)
  }

  val standardParams = NodeParams(
    sendBlocks = true,
    sendTransactions = true,
    ignoreBlocks = true,
    ignoreTransactions = true,
    isMining = true,
    miner = recipientAddress,
    miningInterval = 2.seconds,
    miningDifficulty = 6,
    miningDifficultyDeviation = 2,
    nodes = Nil)

  val unminedBlock = UnminedBlock(
    blockNumber = 1,
    parentHash = CoinNode.genesisBlock.hash,
    transactions = List.empty,
    miner = Address(pub),
    blockDifficulty = standardParams.miningDifficulty,
    totalDifficulty = standardParams.miningDifficulty + CoinNode.genesisBlock.totalDifficulty)

  val minedBlock: MinedBlock = {
    val (pow, nonce) = MinerPoW.mineBlock(unminedBlock.hash, unminedBlock.blockDifficulty)
    MinedBlock(unminedBlock, pow, nonce)
  }
}