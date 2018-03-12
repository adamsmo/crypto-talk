package presentation

import actors.Logic.State
import actors.Node.{ ConnectNode, GetState, NodeParams }
import actors.Wallet.{ Balance, CheckBalance, SendCoins }
import actors.{ Node, Wallet }
import akka.actor.ActorRef
import crypto.ECDSA
import domain.Address

class Transaction extends TestSetup {
  "Node" should "send founds from on wallet to another" in new Env {
    val params: NodeParams = standardParams.copy(
      miningDifficulty = 11,
      miningDifficultyDeviation = 10)

    val (nodes, keys) = (for {
      n <- 1 to 5
    } yield {
      val (prvKey, pubKey) = ECDSA.generateKeyPair()
      val miner = Address(pubKey)
      (system.actorOf(Node.props(params.copy(miner = miner)), s"node-$n"), (prvKey, pubKey))
    }).unzip

    nodes.foreach(node => nodes.foreach(_ ! ConnectNode(node)))

    val node: ActorRef = nodes.head
    val (nodePrivateKey, nodePublicKey) = keys.head
    val minerWallet: ActorRef = system.actorOf(Wallet.props(nodePrivateKey, nodePublicKey, "miner wallet", node))
    val emptyWallet: ActorRef = system.actorOf(Wallet.props(prv, pub, "empty wallet", node))

    eventually {
      nodes.head ! GetState
      val state: State = expectMsgType[State]
      state.getLatestBalance(Address(nodePublicKey)).exists(_ > 4) shouldBe true
    }

    minerWallet ! SendCoins(3, 1, Address(pub))

    eventually {
      nodes.head ! GetState
      val state: State = expectMsgType[State]
      state.getLatestBalance(Address(pub)).contains(3) shouldBe true
    }

    eventually {
      emptyWallet ! CheckBalance
      expectMsg(Balance(3))
    }

    nodes.head ! GetState
    val afterTest: State = expectMsgType[State]
    afterTest.getBlocks.foreach { block =>
      log.info(s"$block")
    }
    afterTest.getLatestAccounts
      .map(_.toString())
      .foreach(log.info)
  }
}
