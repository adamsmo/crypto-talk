package actors

import actors.Logic.State
import actors.Node._
import akka.actor.{ Actor, ActorLogging, ActorRef, Props, Stash }
import akka.util.{ ByteString, Timeout }
import domain._
import org.bouncycastle.util.encoders.Hex

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Random

class Node(nodeParams: NodeParams) extends Actor with ActorLogging with Stash {

  implicit val ctx: ExecutionContext = context.system.dispatcher
  implicit val askTimeOut: Timeout = 5.seconds

  //if node is mining start immediately
  override def preStart(): Unit = {
    if (nodeParams.isMining) {
      self ! MineBlock
    }
  }

  override def receive: Receive = {
    //todo 2.1 initial setup of every node
    standardOperation(
      State(
        chain = List((Node.genesisBlock, Map.empty)),
        txPool = List.empty,
        minerAddress = Some(nodeParams.miner),
        nodes = nodeParams.nodes))
  }

  private def standardOperation(state: State): Receive = {
    //todo 2.2 asynchronously mine new block
    case MineBlock =>
      Future {
        import nodeParams._

        //artificial differentiators of difficulty so nodes will exchange blocks
        //instead of keeping own chain because of same difficulty on all blocks
        val d = Random.nextInt(miningDifficultyDeviation.toInt)
        val targetBlockDifficulty = miningDifficulty + d

        Logic.prepareBlock(state, targetBlockDifficulty).foreach { b =>
          val (pow, nonce) = MinerPoW.mineBlock(b.hash, targetBlockDifficulty)
          self ! MinedBlock(b, pow, nonce)
          log.info(s"block number ${b.blockNumber} mined")
          if (nodeParams.isMining) {
            self ! MineBlock
          }
        }
      }

    //todo 2.4 got new valid block from other node
    case newBlock: MinedBlock if Logic.isValid(newBlock, state) =>
      Logic.getParent(newBlock, state) match {
        //todo 2.4.1 next block, just accept
        case Some(parent) if state.latestBlock().contains(parent) =>
          Logic.executeBlock(newBlock, state) match {
            case Some(newState) =>
              context.become(standardOperation(newState))
              sendToOthers(newBlock, state.nodes)
              log.info(
                s"accepting block ${newBlock.blockNumber} from ${sender().path.name}")
            case None =>
              log.info(s"rejecting block $newBlock")
          }

        //todo 2.4.2 next block that replaces latest block
        case Some(parent) if state
          .latestBlock()
          .exists(latest =>
            latest.totalDifficulty < newBlock.totalDifficulty) =>
          Logic.executeBlock(newBlock, state.rollBack(parent.hash)) match {
            case Some(newState) =>
              context.become(standardOperation(newState))
              sendToOthers(newBlock, state.nodes)
              log.info(
                s"accepting block ${newBlock.blockNumber} from ${sender().path.name}")
            case None =>
              log.info(s"rejecting block $newBlock")
          }

        //todo 2.4.3 potential longer fork, try to resolve
        case None if state
          .latestBlock()
          .exists(_.totalDifficulty < newBlock.totalDifficulty) =>
          log.info("resolving fork")
          context.become(resolvingFork(state, List(newBlock)))
          //this should start timeout for case when sender goes down
          sender() ! GetBlock(newBlock.parentHash)

        //todo 2.4.4 block to ignore, if invalid should update sender reputation
        case _ =>
          log.info(
            s"discarding block ${newBlock.blockNumber} from ${sender().path.name} TD <= latest block ${state.latestBlock().map(_.blockNumber)}")
      }

    case GetBlock(hash) =>
      state.getBlocks
        .find(block => block.hash == hash)
        .foreach(block => sender() ! block)

    case GetLatestBlock =>
      state.latestBlock().foreach { block => sender() ! block }

    //todo 2.5 got new valid tx from other node
    case tx: SignedTransaction if tx.sender.nonEmpty && !state.txPool.contains(
      tx) && tx.amount > 0 && tx.txFee >= 0 =>
      context.become(
        standardOperation(state.copy(txPool = (tx :: state.txPool).distinct)))
      sendToOthers(tx, state.nodes)

    case GetTransactionInfo(hash) =>
      state.getBlocks
        .flatMap(b => b.transactions.map(tx => (tx, b.blockNumber)))
        .find { case (tx, _) => tx.hash == hash }
        .foreach {
          case (tx, blockNumber) => sender() ! TransactionInfo(tx, blockNumber)
        }

    //todo 2.6 new node connected
    case ConnectNode(node) =>
      import state._
      //log.info(s"now connected to $node")
      context.become(standardOperation(state.copy(nodes = node :: nodes)))

    case GetState =>
      sender() ! state
  }

  private def resolvingFork(state: State, branch: List[MinedBlock]): Receive = {
    case newBlock: MinedBlock if branch.lastOption.exists(_.parentHash == newBlock.hash) =>
      Logic.getParent(newBlock, state) match {
        case Some(parent) if Logic.isValid(branch :+ newBlock) =>
          log.info("switching branch")
          context.become(
            executingBranch(state, state.rollBack(parent.hash), (branch :+ newBlock).reverse))
          self ! ExecuteBranch

        case None if Logic.isValid(branch :+ newBlock) && newBlock.blockNumber > 0 =>
          context.become(resolvingFork(state, branch :+ newBlock))
          //this should start timeout for case when sender goes down
          sender() ! GetBlock(newBlock.parentHash)

        case _ =>
          self ! FailToResolveFork
          log.info(s"fail to resolve fork rolling back to normal operation")
      }

    case FailToResolveFork =>
      unstashAll()
      context.become(standardOperation(state))
      log.info(s"fail to resolve fork rolling back to normal operation")

    case _ =>
      stash()
  }

  private def executingBranch(
    oldState: State,
    currentState: State,
    reverseBranch: List[MinedBlock]): Receive = {
    case ExecuteBranch =>
      reverseBranch match {
        case block :: rest =>
          Logic.executeBlock(block, currentState) match {
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
      log.info(s"fork resolved")

    case FailToResolveFork =>
      unstashAll()
      context.become(standardOperation(oldState))
      log.info(s"fail to resolve fork rolling back to normal operation")

    case _ =>
      stash()
  }

  private def sendToOthers(msg: MinedBlock, nodes: List[ActorRef]): Unit =
    if (nodeParams.sendBlocks) {
      //todo 6.1 delay to simulate network lag
      akka.pattern.after(nodeParams.networkDelay, context.system.scheduler)(
        Future {
          nodes
            .filter(node => node != self || node != sender())
            .foreach(node => node ! msg)
        })
    }

  private def sendToOthers(
    msg: SignedTransaction,
    nodes: List[ActorRef]): Unit =
    if (nodeParams.sendTransactions) {
      nodes
        .filter(node => node != self || node != sender())
        .foreach(node => node ! msg)
    }

}

case object Node {

  val genesisBlock: MinedBlock = MinedBlock(
    blockNumber = 0,
    parentHash = ByteString(Hex.decode("00" * 32)),
    transactions = Nil,
    miner = Address(PubKey(42, 42)),
    nonce = ByteString("42"),
    powHash = ByteString("42"),
    blockDifficulty = 0,
    totalDifficulty = 0)

  def props(params: NodeParams): Props = Props(new Node(params))

  case class GetBlock(hash: ByteString)

  case class GetTransactionInfo(hash: ByteString)

  case class TransactionInfo(tx: SignedTransaction, blockNumber: BigInt)

  case class ConnectNode(node: ActorRef)

  case class NodeParams(
      sendBlocks: Boolean,
      sendTransactions: Boolean,
      isMining: Boolean,
      miner: Address,
      miningInterval: FiniteDuration,
      miningDifficulty: BigInt,
      miningDifficultyDeviation: BigInt,
      nodes: List[ActorRef],
      networkDelay: FiniteDuration = 0.seconds)

  case object MineBlock

  case object GetLatestBlock

  case object ForkResolved

  case object FailToResolveFork

  case object ExecuteBranch

  case object GetState
}
