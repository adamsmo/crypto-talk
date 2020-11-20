package presentation.scenarios

import actors.Logic.State
import actors.Node.{ GetState, NodeParams }
import presentation.{ Env, TestSetup }

import scala.concurrent.duration._

class EasyMining extends TestSetup {
  "Node" should "diverge with slow network" in new Env {
    //todo 6.2 slow network
    val params: NodeParams =
      standardParams.copy(
        miningDifficulty = 15,
        miningDifficultyDeviation = 1,
        networkDelay = 2.second)

    val (network, _) =
      generateNodes(nodesCount = 3, name = "slow-net", system, params).unzip
    connectAll(network)

    Thread.sleep(5.seconds.toMillis)

    network.foreach { node =>
      log.info("\n\n")
      node ! GetState
      expectMsgType[State].getBlocks.foreach { block =>
        log.info(s"${block.miner}")
      }
    }
  }

  it should "diverge with fast network" in new Env {
    //todo 6.3 fast network
    val params: NodeParams =
      standardParams.copy(
        miningDifficulty = 15,
        miningDifficultyDeviation = 1,
        networkDelay = 0.second)

    val (network, _) =
      generateNodes(nodesCount = 3, name = "fast-net", system, params).unzip
    connectAll(network)

    Thread.sleep(5.seconds.toMillis)

    network.foreach { node =>
      log.info("\n\n")
      node ! GetState
      expectMsgType[State].getBlocks.foreach { block =>
        log.info(s"${block.miner}")
      }
    }
  }
}
