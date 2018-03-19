package actors

import actors.CoinLogic.State
import actors.CoinNode.{ ConnectNode, GetState, MineBlock, NodeParams }
import akka.actor.{ ActorRef, ActorSystem, PoisonPill }
import akka.testkit.{ ImplicitSender, TestKit }
import crypto.ECDSA
import domain._
import org.scalatest.concurrent.Eventually
import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.duration._

class CoinNodeSpec extends TestKit(ActorSystem("MySpec"))
  with FlatSpecLike
  with Matchers
  with ImplicitSender
  with BeforeAndAfterAll
  with Eventually {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 1.second)

  val log: Logger = LoggerFactory.getLogger(this.getClass)

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
    val minerAccount: Option[Account] = nodeState.chain.headOption
      .flatMap { case (_, accounts) => accounts.get(recipientAddress) }
    minerAccount should contain(Account(0, 3 * CoinLogic.minerReward))

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

  it should "many blocks with lower difficulty outweighs one block with greater difficulty" in new Env {
    fail()
  }

  //wallet
  it should "send founds from on wallet to another" in new Env {
    val params: NodeParams = standardParams.copy(
      miningInterval = 2.seconds,
      miningDifficulty = 11,
      miningDifficultyDeviation = 10)

    val (nodes, prvKeys) = (for {
      n <- 1 to 5
    } yield {
      val (prvKey, pubKey) = ECDSA.generateKeyPair()
      val miner = Address(pubKey)
      (system.actorOf(CoinNode.props(params.copy(miner = miner)), s"node-$n"), prvKey)
    }).unzip

    nodes.foreach(node => nodes.foreach(_ ! ConnectNode(node)))

    while (true) {
      Thread.sleep(3.seconds.toMillis)
      nodes.head ! GetState
      val m: State = expectMsgType[State](5.seconds)
      log.info(s"block number: ${m.latestBlock().map(_.blockNumber)} - ${m.latestBlock().map(_.miner)}")
      log.info(s"transactions: ${m.latestBlock().map(_.transactions).getOrElse(Nil)}")
      log.info(s"accounts: \n${m.chain.headOption.map { case (_, a) => a.toList }.getOrElse(Nil).mkString("\n")}")
    }

    fail()
  }

  it should "update account balance" in new Env {
    fail()
  }
}

trait Env {
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