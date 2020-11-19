package presentation.scenarios

import actors.Logic.State
import actors.Node.{ GetState, NodeParams }
import domain.Address
import presentation.{ Env, TestSetup }

import scala.concurrent.duration._

class BigFireWall extends TestSetup {
  "Node" should "resolve long fork, blockchain split brain" in new Env {

    //todo first part of the network 3 nodes
    val biggerParams: NodeParams =
      standardParams.copy(miningDifficulty = 15, miningDifficultyDeviation = 1)
    val (biggerNetwork, biggerKeys) =
      generateNodes(nodesCount = 1, name = "bigger-net", system, biggerParams).unzip
    connectAll(biggerNetwork)

    //todo second part of the network 1 node
    val smallerParams: NodeParams =
      standardParams.copy(miningDifficulty = 15, miningDifficultyDeviation = 1)
    val (smallerNetwork, smallerKeys) =
      generateNodes(nodesCount = 1, name = "smaller-net", system, smallerParams).unzip

    //todo let the networks mine in separation
    Thread.sleep(5.seconds.toMillis)

    smallerNetwork.head ! GetState
    val state1: State = expectMsgType[State]
    val smallerNetCoins1: BigInt = state1.getLatestBalance(Address(smallerKeys.head._2)).getOrElse(0)
    println(s"smaller network mined $smallerNetCoins1 coins")

    biggerNetwork.head ! GetState
    val state11: State = expectMsgType[State]
    val biggerNetCoins1: BigInt = state11.getLatestBalance(Address(biggerKeys.head._2)).getOrElse(0)
    println(s"bigger network mined $biggerNetCoins1 coins")

    //todo reunion
    connectAll(biggerNetwork ++ smallerNetwork)
    Thread.sleep(2.seconds.toMillis)

    smallerNetwork.head ! GetState
    val state2: State = expectMsgType[State]
    val smallerNetCoins2: BigInt = state2.getLatestBalance(Address(smallerKeys.head._2)).getOrElse(0)
    println(s"smaller network coins after reunion $smallerNetCoins2")

    biggerNetwork.head ! GetState
    val state22: State = expectMsgType[State]
    val biggerNetCoins2: BigInt = state22.getLatestBalance(Address(biggerKeys.head._2)).getOrElse(0)
    println(s"bigger network mined $biggerNetCoins2 coins")

    state2.chain.foreach { block =>
      log.info(s"$block")
    }

  }
}
