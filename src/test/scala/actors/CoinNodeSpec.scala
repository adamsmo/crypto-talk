package actors

import actors.CoinNode.{ GetState, MineBlock, NodeParams, State }
import akka.actor.{ ActorRef, ActorSystem, PoisonPill }
import akka.testkit.{ ImplicitSender, TestKit }
import crypto.ECDSA
import domain._
import org.scalatest.concurrent.Eventually
import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }

import scala.concurrent.duration._

class CoinNodeSpec extends TestKit(ActorSystem("MySpec"))
  with FlatSpecLike
  with Matchers
  with ImplicitSender
  with BeforeAndAfterAll
  with Eventually {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 1.second)

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  //mining
  "CoinNode" should "mine new consecutive blocks and send them" in new Env {
    val node: ActorRef = system.actorOf(CoinNode.props(standardParams.copy(
      nodes = List(self),
      isMining = false)))

    node ! MineBlock
    expectMsgClass(classOf[MinedBlock]).blockNumber shouldBe 1

    node ! MineBlock
    expectMsgClass(classOf[MinedBlock]).blockNumber shouldBe 2

    node ! MineBlock
    expectMsgClass(classOf[MinedBlock]).blockNumber shouldBe 3

    node ! GetState
    val nodeState: State = expectMsgClass(classOf[State])
    val minerAccount: Option[Account] = nodeState.chain.headOption.flatMap { case (_, accounts) => accounts.get(minerAddress) }
    minerAccount should contain(Account(0, 3 * CoinNode.minerReward))

    node ! PoisonPill
  }

  //notwork operation
  it should "reject invalid blocks" in new Env {
    val node: ActorRef = system.actorOf(CoinNode.props(standardParams.copy(
      nodes = List(self),
      isMining = false)))

    node ! CoinNode.genesisBlock.copy(blockNumber = 42, totalDifficulty = 9001)
    expectNoMessage(3.seconds)

    node ! GetState
    val nodeState: State = expectMsgClass(classOf[State])
    nodeState.latestBlock() should contain(CoinNode.genesisBlock)

    node ! PoisonPill
  }

  it should "accept valid blocks" in new Env {
    val node: ActorRef = system.actorOf(CoinNode.props(standardParams.copy(
      nodes = List(self),
      isMining = false,
      sendBlocks = false)))

    node ! minedBlock
    expectNoMessage(5.seconds)

    node ! GetState
    val nodeState: State = expectMsgClass(classOf[State])
    nodeState.latestBlock() should contain(minedBlock)

    node ! PoisonPill
  }

  it should "reject invalid fork with grater total difficulty" in new Env {
    fail()
  }

  it should "switch to valid for with grater total difficulty" in new Env {
    fail()
  }

  it should "resolve forks after network split" in new Env {
    fail()
  }

  //wallet
  it should "send founds from wallet" in new Env {
    fail()
  }

  it should "update account balance" in new Env {
    fail()
  }
}

trait Env {
  val (prv, pub) = ECDSA.generateKeyPair()
  val minerAddress = Address(pub)

  val standardParams = NodeParams(
    sendBlocks = true,
    sendTransactions = true,
    ignoreBlocks = true,
    ignoreTransactions = true,
    isMining = true,
    miner = minerAddress,
    miningInterval = 2.seconds,
    miningTargetDifficulty = 5,
    nodes = Nil)

  val unminedBlock = UnminedBlock(
    blockNumber = 1,
    parentHash = CoinNode.genesisBlock.hash,
    transactions = List.empty,
    miner = Address(pub),
    blockDifficulty = standardParams.miningTargetDifficulty,
    totalDifficulty = standardParams.miningTargetDifficulty + CoinNode.genesisBlock.totalDifficulty)

  val minedBlock: MinedBlock = {
    val (pow, nonce) = MinerPoW.mineBlock(unminedBlock.hash, unminedBlock.blockDifficulty)
    MinedBlock(unminedBlock, pow, nonce)
  }
}