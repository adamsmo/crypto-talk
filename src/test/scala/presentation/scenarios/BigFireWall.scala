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
    val (biggerNetwork, _) =
      generateNodes(nodesCount = 3, name = "bigger-net", system, biggerParams).unzip
    connectAll(biggerNetwork)

    //todo second part of the network 1 node
    val smallerParams: NodeParams =
      standardParams.copy(miningDifficulty = 15, miningDifficultyDeviation = 1)
    val (smallerNetwork, smallerKeys) =
      generateNodes(nodesCount = 1, name = "smaller-net", system, smallerParams).unzip

    //todo let the networks mine in separation
    Thread.sleep(5.seconds.toMillis)

    smallerNetwork.head ! GetState
    val stateBefore: State = expectMsgType[State]
    val smallerNetCoinsBefore: BigInt = stateBefore.getLatestBalance(Address(smallerKeys.head._2)).getOrElse(0)
    println(s"smaller network mined $smallerNetCoinsBefore coins")

    //todo reunion
    connectAll(biggerNetwork ++ smallerNetwork)
    Thread.sleep(2.seconds.toMillis)

    smallerNetwork.head ! GetState
    val stateAfter: State = expectMsgType[State]
    val smallerNetCoinsAfter: BigInt = stateAfter.getLatestBalance(Address(smallerKeys.head._2)).getOrElse(0)
    println(s"smaller network coins after reunion $smallerNetCoinsAfter")

    stateAfter.chain.foreach { block =>
      log.info(s"$block")
    }

  }
}
