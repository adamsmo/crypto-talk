package actors

import actors.CoinNode._
import akka.actor.{ Actor, ActorLogging, Stash }
import akka.pattern.ask
import akka.util.{ ByteString, Timeout }
import domain._
import org.bouncycastle.util.encoders.Hex

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class CoinNode extends Actor with ActorLogging with Stash {

  implicit val ctx: ExecutionContext = context.system.dispatcher
  implicit val askTimeOut: Timeout = 5.seconds

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

    case newBlock: Block =>
      getParent(newBlock, state) match {
        case Some(parent) if state.latestBlock().contains(parent) =>
          executeBlock(newBlock, state) match {
            case Some(newState) =>
              context.become(standardOperation(newState))
            case None =>
              log.info(s"rejecting block $newBlock")
          }

        case Some(parent) if state.latestBlock().exists(latest => latest.totalDifficulty < newBlock.totalDifficulty) =>
          executeBlock(newBlock, state.rollBack(parent.hash)) match {
            case Some(newState) =>
              context.become(standardOperation(newState))
            case None =>
              log.info(s"rejecting block $newBlock")
          }

        case Some(_) =>
          log.info(s"discarding block $newBlock as it has lower total difficulty than current latest block")

        case None if state.latestBlock().exists(_.totalDifficulty < newBlock.totalDifficulty) =>
          context.become(resolvingFork(state, List(newBlock)))

          (sender() ? GetBlock(newBlock.parentHash)).mapTo[Block].onComplete {
            case Success(response) =>
              self ! response
            case Failure(_) =>
              self ! FailToResolveFork
          }
      }

    case GetState =>
      sender() ! state
  }

  def executingBranch(oldState: State, currentState: State, reverseBranch: List[Block]): Receive = {
    case ExecuteBranch =>
      reverseBranch match {
        case block :: rest =>
          executeBlock(block, currentState) match {
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

  private def resolvingFork(state: State, branch: List[Block]): Receive = {
    //process only if block is part of the branch
    case newBlock: Block if branch.headOption.exists(_.parentHash == newBlock.hash) =>
      getParent(newBlock, state) match {
        case Some(_) if isValid(branch :+ newBlock) =>
          context.become(executingBranch(state, state, (branch :+ newBlock).reverse))
          self ! ExecuteBranch

        case None if isValid(branch :+ newBlock) =>
          context.become(resolvingFork(state, branch :+ newBlock))

          (sender() ? GetBlock(newBlock.parentHash)).mapTo[Block].onComplete {
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

  private def getParent(block: Block, state: State): Option[Block] = {
    state.chain
      .find { case (chainBlock, _) => chainBlock.hash == block.parentHash }
      .map { case (chainBlock, _) => chainBlock }
  }

  private def executeBlock(block: Block, state: State): Option[State] = {
    if (isValid(block, state)) {
      val initialAccounts = state.chain.headOption.map { case (_, acc) => acc }

      block.transactions
        //execute all transactions
        .foldLeft(initialAccounts) {
          case (accounts, tx) =>
            accounts.flatMap(executeTransaction(tx, _))
        }
        //pay miner
        .map { accounts =>
          val minerAcc = accounts.getOrElse(block.miner, Account.empty)
          val txFees = block.transactions.map(_.txFee).sum
          accounts.updated(block.miner, minerAcc.add(txFees + CoinNode.minerReward))
        }
        //construct new state
        .map { accounts =>
          state.copy(chain = (block, accounts) :: state.chain, txPool = state.txPool.diff(block.transactions))
        }
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

  private def isValid(branch: List[Block]): Boolean = branch match {
    case b1 :: b2 :: rest =>
      b1.totalDifficulty - b1.blockDifficulty == b2.totalDifficulty && b1.parentHash == b2.hash && isValid(rest)
    case _ :: Nil =>
      true
    case Nil =>
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

  private def executeTransaction(tx: Transaction, state: Map[Address, Account]): Option[Map[Address, Account]] = {
    for {
      sender <- tx.sender
      recipientAcc <- state.get(tx.recipient).orElse(Some(Account.empty))
      senderAcc <- state.get(sender) if senderAcc.balance >= tx.txFee + tx.amount && senderAcc.txNumber == tx.txNumber
    } yield {
      state
        .updated(tx.recipient, recipientAcc.add(tx.amount))
        .updated(sender, senderAcc.subtract(tx.amount + tx.txFee))
    }
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
      minerAddress: Option[Address]) {

    def rollBack(hash: ByteString): State = {
      val (blocksToDiscard, commonPrefix) = chain.span { case (block, _) => block.hash != hash }
      val transactionToAdd = blocksToDiscard.flatMap { case (block, _) => block.transactions }

      copy(
        chain = commonPrefix,
        txPool = txPool ++ transactionToAdd)
    }

    def latestBlock(): Option[Block] = {
      chain.headOption.map { case (block, _) => block }
    }
  }

  case object MineBlock

  case object GetLatestBlock

  case class GetBlock(query: ByteString)

  case object ForkResolved

  case object FailToResolveFork

  case object ExecuteBranch

  //for testing
  case object GetState
}