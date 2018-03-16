package actors

import actors.CoinNode.{ MineBlock, State }
import akka.actor.{ Actor, ActorLogging }
import akka.util.ByteString
import domain._
import org.bouncycastle.util.encoders.Hex

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CoinNode extends Actor with ActorLogging {

  override def preStart(): Unit = {
    self ! MineBlock
  }

  override def receive: Receive = node(State(
    chain = List((CoinNode.genesisBlock, Map.empty)),
    txPool = List.empty,
    minerAddress = None))

  private def node(state: State): Receive = {
    case MineBlock =>
      state.minerAddress.flatMap(miner => preaperBlock(state))

      val b = preaperBlock(state)
      Future {
        b.foreach { unminedBlock =>
          val (pow, nonce) = MinerPoW.mineBlock(unminedBlock.hash, CoinNode.difficulty)
          self ! Block(unminedBlock, pow, nonce)
          self ! MineBlock
        }
      }
  }

  private def preaperBlock(state: State): Option[UnminedBlock] = state.minerAddress.flatMap { miner =>
    state.chain match {
      case (parent, accounts) :: chain =>
        val transactions = selectTransactions(accounts, state.txPool)
        Some(UnminedBlock(
          parent.blockNumber + 1,
          parent.hash,
          transactions,
          miner,
          CoinNode.difficulty,
          parent.totalDifficulty + CoinNode.difficulty))
      case _ =>
        None
    }
  }

  private def selectTransactions(accounts: Map[Address, Account], txPool: List[Transaction]): List[Transaction] = {
    txPool
      .sortBy(tx => tx.txFee)
      .filter { tx =>
        val sender = Address(tx.signature.pubKey)
        accounts.get(sender).exists { acc =>
          acc.txNumber == tx.txNumber && tx.amount + tx.txFee < acc.balance
        }
      }
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