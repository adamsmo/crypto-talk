package presentation

import actors.CoinLogic.State
import actors.CoinNode
import actors.CoinNode.GetState
import akka.actor.{ ActorRef, PoisonPill }
import scala.concurrent.duration._

class ForkResolving extends TestSetup {
  "CoinNode" should "reject invalid blocks" in new Env {
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
}
