package presentation

import actors.Node
import actors.Node.{ ConnectNode, NodeParams }
import akka.actor.{ ActorRef, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKit }
import akka.util.Timeout
import crypto.ECDSA
import domain._
import org.scalatest.concurrent.Eventually
import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import org.slf4j.{ Logger, LoggerFactory }

import scala.collection.immutable
import scala.concurrent.duration._

class TestSetup
  extends TestKit(ActorSystem("for-presentation"))
  with FlatSpecLike
  with Matchers
  with ImplicitSender
  with BeforeAndAfterAll
  with Eventually {
  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 20.seconds, interval = 1.second)
}

trait Env {
  implicit val askTimeOut: Timeout = 5.seconds

  val log: Logger = LoggerFactory.getLogger(this.getClass)

  val (prv, pub) = ECDSA.generateKeyPair()
  val recipientAddress: Address = Address(pub)
  val standardParams: NodeParams = NodeParams(
    sendBlocks = true,
    sendTransactions = true,
    isMining = true,
    miner = recipientAddress,
    miningInterval = 2.seconds,
    miningDifficulty = 6,
    miningDifficultyDeviation = 2,
    nodes = Nil)
  val unminedBlock: UnminedBlock = UnminedBlock(
    blockNumber = 1,
    parentHash = Node.genesisBlock.hash,
    transactions = List.empty,
    miner = Address(pub),
    blockDifficulty = standardParams.miningDifficulty,
    totalDifficulty =
      standardParams.miningDifficulty + Node.genesisBlock.totalDifficulty)
  val minedBlock: MinedBlock = {
    val (pow, nonce) =
      MinerPoW.mineBlock(unminedBlock.hash, unminedBlock.blockDifficulty)
    MinedBlock(unminedBlock, pow, nonce)
  }

  def generateMinerAddress(): Address = {
    val (_, pub) = ECDSA.generateKeyPair()
    Address(pub)
  }

  def generateNodes(
    nodesCount: Int,
    name: String,
    system: ActorSystem,
    params: NodeParams): immutable.Seq[(ActorRef, (PrvKey, PubKey))] =
    for {
      n <- 1 to nodesCount
    } yield {
      val (prvKey, pubKey) = ECDSA.generateKeyPair()
      val miner = Address(pubKey)
      (
        system.actorOf(Node.props(params.copy(miner = miner)), s"$name-$n"),
        (prvKey, pubKey))
    }

  def connectAll(nodes: Seq[ActorRef]): Unit =
    nodes.foreach(node => nodes.foreach(_ ! ConnectNode(node)))
}
