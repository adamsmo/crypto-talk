package actors

import actors.CoinNode.{ MineBlock, State }
import akka.actor.{ Actor, ActorLogging, Stash }
import akka.util.ByteString
import domain._
import org.bouncycastle.util.encoders.Hex

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

class CoinNode extends Actor with ActorLogging with Stash {

  implicit val ctx: ExecutionContext = context.system.dispatcher

  override def preStart(): Unit = {
    context.system.scheduler.schedule(
      initialDelay = 5.seconds,
      interval = 5.seconds,
      receiver = self,
      message = MineBlock)
  }

  override def receive: Receive = standardOperation(
    State(
      chain = List((CoinNode.genesisBlock, Map.empty)),
      txPool = List.empty,
      minerAddress = None))

  private def standardOperation(state: State): Receive = {
    case MineBlock =>
      Future {
        prepareBlock(state).foreach { b =>
          val (pow, nonce) = MinerPoW.mineBlock(b.hash, CoinNode.difficulty)
          self ! Block(b, pow, nonce)
        }
      }

    case b: Block =>
      //check if parent is in chain
      executeBlock(b, state) match {
        case Some(newState) =>
          context.become(standardOperation(newState))
        case None =>
          log.info(s"rejecting block $b")
      }
  }

  private def resolvingBranch(state: State, branch: List[Block]): Receive = {
    case _ =>
  }

  private def executeBlock(block: Block, state: State): Option[State] = {
    if (isValid(block, state)) {
      //make transfers to accounts + update tx numbers
      //add tx fee to miner acc
      //add mining reward
      ???
    } else {
      None
    }
  }

  private def isValid(block: Block, state: State): Boolean = {
    //check POW
    //check difficulty
    //check tx limit
    //check that txs senders are in state and have enough balance
    true
  }

  private def prepareBlock(state: State): Option[UnminedBlock] = {
    state match {
      case State((parent, accounts) :: chain, txPool, Some(miner)) =>
        Some(UnminedBlock(
          blockNumber = parent.blockNumber + 1,
          parentHash = parent.hash,
          transactions = selectTransactions(accounts, txPool),
          miner = miner,
          blockDifficulty = CoinNode.difficulty,
          totalDifficulty = parent.totalDifficulty + CoinNode.difficulty))
      case _ =>
        None
    }
  }

  private def selectTransactions(accounts: Map[Address, Account], txPool: List[Transaction]): List[Transaction] = {
    txPool
      .filter { tx =>
        val sender = Address(tx.signature.pubKey)
        accounts.get(sender).exists { acc =>
          acc.txNumber == tx.txNumber && tx.amount + tx.txFee < acc.balance
        }
      }
      .sortBy(tx => tx.txFee)
      .take(CoinNode.maxTransactionsPerBlock)
  }
}

case object CoinNode {

  val difficulty = 20
  val maxTransactionsPerBlock = 5
  val minerReward = 5000000

  val genesisBlock = Block(
    blockNumber = 0,
    parentHash = ByteString(Hex.decode("00" * 32)),
    transactions = Nil,
    miner = Address(PubKey(42, 42)),
    nonce = ByteString("42"),
    powHash = ByteString("42"),
    blockDifficulty = 0,
    totalDifficulty = 0)

  case class State(
      chain: List[(Block, Map[Address, Account])],
      txPool: List[Transaction],
      minerAddress: Option[Address])

  case object MineBlock

}