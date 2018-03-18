package actors

import actors.CoinLogic.State
import actors.CoinNode._
import akka.actor.{ Actor, ActorLogging, ActorRef, Props, Stash }
import akka.pattern.ask
import akka.util.{ ByteString, Timeout }
import domain._
import org.bouncycastle.util.encoders.Hex

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class CoinNode(nodeParams: NodeParams) extends Actor with ActorLogging with Stash {

  implicit val ctx: ExecutionContext = context.system.dispatcher
  implicit val askTimeOut: Timeout = 5.seconds

  override def preStart(): Unit = {
    if (nodeParams.isMining) {
      context.system.scheduler.schedule(
        initialDelay = 0.seconds,
        interval = nodeParams.miningInterval,
        receiver = self,
        message = MineBlock)
    }
  }

  override def receive: Receive = standardOperation(
    State(
      chain = List((CoinNode.genesisBlock, Map.empty)),
      txPool = List.empty,
      minerAddress = Some(nodeParams.miner),
      nodes = nodeParams.nodes))

  private def standardOperation(state: State): Receive = {
    case MineBlock =>
      Future {
        CoinLogic.prepareBlock(state, nodeParams.miningDifficulty).foreach { b =>
          val (pow, nonce) = MinerPoW.mineBlock(b.hash, nodeParams.miningDifficulty)
          self ! MinedBlock(b, pow, nonce)
        }
      }

    case newBlock: MinedBlock if CoinLogic.isValid(newBlock, state) =>
      CoinLogic.getParent(newBlock, state) match {
        case Some(parent) if state.latestBlock().contains(parent) =>
          CoinLogic.executeBlock(newBlock, state) match {
            case Some(newState) =>
              context.become(standardOperation(newState))
              sendToOthers(newBlock)
            case None =>
              log.info(s"rejecting block $newBlock")
          }

        case Some(parent) if state.latestBlock().exists(latest => latest.totalDifficulty < newBlock.totalDifficulty) =>
          CoinLogic.executeBlock(newBlock, state.rollBack(parent.hash)) match {
            case Some(newState) =>
              context.become(standardOperation(newState))
              sendToOthers(newBlock)
            case None =>
              log.info(s"rejecting block $newBlock")
          }

        case None if state.latestBlock().exists(_.totalDifficulty < newBlock.totalDifficulty) =>
          context.become(resolvingFork(state, List(newBlock)))

          (sender() ? GetBlock(newBlock.parentHash)).mapTo[MinedBlock].onComplete {
            case Success(response) =>
              self ! response
            case Failure(_) =>
              self ! FailToResolveFork
          }

        case _ =>
          log.info(s"discarding block $newBlock as it has lower total difficulty than current latest block")
      }

    case GetLatestBlock =>
      state.chain.headOption.foreach { case (block, _) => sender() ! block }

    case GetTransactions =>
      sender() ! Transactions(state.txPool)

    case tx: Transaction if tx.sender.nonEmpty =>
      context.become(standardOperation(state.copy(txPool = (tx :: state.txPool).distinct)))
      sendToOthers(tx)

    case Transactions(txs) if txs.forall(tx => tx.sender.nonEmpty) =>
      context.become(standardOperation(state.copy(txPool = (state.txPool ++ txs).distinct)))
      txs.foreach(sendToOthers)

    case ConnectNode(node) =>
      import state._
      context.become(standardOperation(state.copy(nodes = node :: nodes)))

    case GetState =>
      sender() ! state
  }

  def executingBranch(oldState: State, currentState: State, reverseBranch: List[MinedBlock]): Receive = {
    case ExecuteBranch =>
      reverseBranch match {
        case block :: rest =>
          CoinLogic.executeBlock(block, currentState) match {
            case Some(newState) =>
              context.become(executingBranch(oldState, newState, rest))
              self ! ExecuteBranch
            case None =>
              self ! FailToResolveFork
          }
        case Nil =>
          self ! ForkResolved
      }
    case ForkResolved =>
      unstashAll()
      context.become(standardOperation(currentState))
      log.info(s"fail to resolve fork rolling back to normal operation")

    case FailToResolveFork =>
      unstashAll()
      context.become(standardOperation(oldState))
      log.info(s"fail to resolve fork rolling back to normal operation")

    case _ =>
      stash()
  }

  private def resolvingFork(state: State, branch: List[MinedBlock]): Receive = {
    case newBlock: MinedBlock if branch.headOption.exists(_.parentHash == newBlock.hash) =>
      CoinLogic.getParent(newBlock, state) match {
        case Some(_) if CoinLogic.isValid(branch :+ newBlock) =>
          context.become(executingBranch(state, state, (branch :+ newBlock).reverse))
          self ! ExecuteBranch

        case None if CoinLogic.isValid(branch :+ newBlock) && newBlock.blockNumber > 0 =>
          context.become(resolvingFork(state, branch :+ newBlock))

          (sender() ? GetBlock(newBlock.parentHash)).mapTo[MinedBlock].onComplete {
            case Success(response) =>
              self ! response
            case Failure(_) =>
              self ! FailToResolveFork
          }
        case _ =>
          self ! FailToResolveFork
      }

    case FailToResolveFork =>
      unstashAll()
      context.become(standardOperation(state))
      log.info(s"fail to resolve fork rolling back to normal operation")

    case _ =>
      stash()
  }

  private def sendToOthers(msg: Block): Unit = if (nodeParams.sendBlocks) {
    nodeParams.nodes.filter(node => node != self).foreach(node => node ! msg)
  }

  private def sendToOthers(msg: Transaction): Unit = if (nodeParams.sendTransactions) {
    nodeParams.nodes.filter(node => node != self).foreach(node => node ! msg)
  }

}

case object CoinNode {

  def props(params: NodeParams): Props = Props(new CoinNode(params))

  case object MineBlock

  case object GetLatestBlock

  case class GetBlock(query: ByteString)

  case object ForkResolved

  case object FailToResolveFork

  case object ExecuteBranch

  case object GetTransactions

  case class Transactions(txs: List[Transaction])

  case class ConnectNode(node: ActorRef)

  //for testing
  case object GetState

  val genesisBlock = MinedBlock(
    blockNumber = 0,
    parentHash = ByteString(Hex.decode("00" * 32)),
    transactions = Nil,
    miner = Address(PubKey(42, 42)),
    nonce = ByteString("42"),
    powHash = ByteString("42"),
    blockDifficulty = 0,
    totalDifficulty = 0)

  case class NodeParams(
      sendBlocks: Boolean,
      sendTransactions: Boolean,
      ignoreBlocks: Boolean,
      ignoreTransactions: Boolean,
      isMining: Boolean,
      miner: Address,
      miningInterval: FiniteDuration,
      miningDifficulty: BigInt,
      miningDifficultyDeviation: BigInt,
      nodes: List[ActorRef])
}