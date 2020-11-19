package presentation

import actors.Logic.State
import actors.Node.{ GetState, MineBlock }
import actors.{ Logic, Node }
import akka.actor.ActorRef
import domain.{ Account, MinedBlock }

class Mining extends TestSetup {
  "Node" should "mine new consecutive blocks and send them" in new Env {
    val node: ActorRef = system.actorOf(
      Node.props(standardParams.copy(nodes = List(self), isMining = false)))

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

    minerAccount should contain(Account(0, 3 * Logic.minerReward))
  }
}
