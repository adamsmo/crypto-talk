package presentation

import actors.CoinLogic.State
import actors.CoinNode
import actors.CoinNode.{ ConnectNode, GetState, NodeParams }
import crypto.ECDSA
import domain.Address

import scala.concurrent.duration._

class Transaction extends TestSetup {
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
