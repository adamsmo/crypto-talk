package presentation.scenarios

import actors.Logic.State
import actors.Node.{ GetState, NodeParams }
import actors.Wallet
import actors.Wallet.{ Balance, CheckBalance, SendCoins }
import akka.actor.ActorRef
import domain.Address
import presentation.{ Env, TestSetup }

class NormalOperation extends TestSetup {
  "Node" should "send founds from on wallet to another" in new Env {
    val params: NodeParams =
      standardParams.copy(miningDifficulty = 11, miningDifficultyDeviation = 10)

    //prepare the network
    val (nodes, keys) =
      generateNodes(nodesCount = 5, name = "node", system, params).unzip
    connectAll(nodes)

    val node: ActorRef = nodes.head
    val (nodePrivateKey, nodePublicKey) = keys.head
    val minerWallet: ActorRef = system.actorOf(
      Wallet.props(nodePrivateKey, nodePublicKey, "Miner Wallet", node))

    //empty wallet not owned by any miner
    val emptyWallet: ActorRef =
      system.actorOf(Wallet.props(prv, pub, "Empty Wallet", node))

    //wait for miner to mine few coins
    eventually {
      nodes.head ! GetState
      val state: State = expectMsgType[State]
      state.getLatestBalance(Address(nodePublicKey)).exists(_ > 4) shouldBe true
    }

    //send coins from miner wallet
    minerWallet ! SendCoins(3, 1, Address(pub))

    //check if transaction is recorded in a block
    eventually {
      nodes.head ! GetState
      val state: State = expectMsgType[State]
      state.getLatestBalance(Address(pub)).contains(3) shouldBe true
    }

    //check that funds are present on target wallet
    eventually {
      emptyWallet ! CheckBalance
      expectMsg(Balance(3))
    }

    log.info("\n\n\nBLOCKCHAIN NOW CONTAINS:")
    //log every block that is part of the blockchain
    nodes.head ! GetState
    val afterTest: State = expectMsgType[State]
    afterTest.getBlocks.foreach { block =>
      log.info(s"$block")
    }

    log.info("\n\n\nACCOUNTS:")
    //log every account balance
    afterTest.getLatestAccounts
      .map(_.toString())
      .foreach(log.info)
  }
}
