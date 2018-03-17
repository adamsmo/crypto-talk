package actors

import actors.CoinNode.NodeParams
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import crypto.ECDSA
import domain.{Address, MinedBlock}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration._

class CoinNodeSpec extends TestKit(ActorSystem("MySpec")) with FlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "CoinNode" should "mine new consecutive blocks and send them" in new Env {
    val node: ActorRef = system.actorOf(CoinNode.props(standardParams.copy(nodes = List(self))))

    expectMsgClass(classOf[MinedBlock]).blockNumber shouldBe 1
    expectMsgClass(classOf[MinedBlock]).blockNumber shouldBe 2
    expectMsgClass(classOf[MinedBlock]).blockNumber shouldBe 3
    expectMsgClass(classOf[MinedBlock]).blockNumber shouldBe 4
  }

  it should "reject invalid blocks" in new Env {
    val node: ActorRef = system.actorOf(CoinNode.props(standardParams.copy(nodes = List(self))))


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
}