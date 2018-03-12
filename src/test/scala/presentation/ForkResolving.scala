package presentation

import actors.Logic.State
import actors.Node
import actors.Node.{ ConnectNode, GetState, MineBlock }
import akka.actor.ActorRef
import domain.MinedBlock

import scala.concurrent.duration._

class ForkResolving extends TestSetup {
  "Node" should "reject invalid blocks" in new Env {
    val node: ActorRef = system.actorOf(Node.props(standardParams.copy(
      nodes = List(self),
      isMining = false)))

    node ! minedBlock.copy(blockNumber = 3)
    expectNoMessage(3.seconds)

    node ! GetState
    val nodeState: State = expectMsgClass(classOf[State])
    nodeState.latestBlock() should contain(Node.genesisBlock)
  }

  it should "accept new valid blocks" in new Env {
    val node: ActorRef = system.actorOf(Node.props(standardParams.copy(
      nodes = List(self),
      isMining = false,
      sendBlocks = false)))

    node ! minedBlock
    expectNoMessage(5.seconds)

    node ! GetState
    val nodeState: State = expectMsgClass(classOf[State])
    nodeState.latestBlock() should contain(minedBlock)
  }

  it should "resolve forks after network split" in new Env {
    val node1: ActorRef = system.actorOf(Node.props(standardParams.copy(
      nodes = List(self),
      isMining = false)), "n1")
    val node2: ActorRef = system.actorOf(Node.props(standardParams.copy(
      nodes = List(self),
      isMining = false)), "n2")
    val node3: ActorRef = system.actorOf(Node.props(standardParams.copy(
      nodes = List(self),
      isMining = false)), "n3")

    for (n <- 1 to 3) {
      node1 ! MineBlock
      expectMsgClass(classOf[MinedBlock]).blockNumber shouldBe n
    }

    for (n <- 1 to 2) {
      node3 ! MineBlock
      expectMsgClass(classOf[MinedBlock]).blockNumber shouldBe n
    }

    for (n <- 1 to 5) {
      node2 ! MineBlock
      expectMsgClass(classOf[MinedBlock]).blockNumber shouldBe n
    }

    //heal network split
    node1 ! ConnectNode(node2)
    node2 ! ConnectNode(node1)
    node1 ! ConnectNode(node3)

    node2 ! MineBlock

    eventually {
      node1 ! GetState
      expectMsgClass(classOf[State]).latestBlock().map(_.blockNumber) shouldBe Some(6)
      node3 ! GetState
      expectMsgClass(classOf[State]).latestBlock().map(_.blockNumber) shouldBe Some(6)
    }
  }

}
