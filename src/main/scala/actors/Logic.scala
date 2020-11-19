package actors

import akka.actor.ActorRef
import akka.util.ByteString
import domain._

import scala.annotation.tailrec

object Logic {
  val maxTransactionsPerBlock = 5
  val minerReward = 5

  //block execution
  def executeBlock(block: MinedBlock, state: State): Option[State] = {
    if (isValid(block, state)) {
      val initialAccounts = state.getAccounts.headOption

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
          accounts.updated(block.miner, minerAcc.add(txFees + minerReward))
        }
        //construct new state
        .map { accounts =>
          state.copy(
            chain = (block, accounts) :: state.chain,
            txPool = state.txPool.diff(block.transactions))
        }
    } else {
      None
    }
  }

  private def executeTransaction(
    tx: SignedTransaction,
    state: Map[Address, Account]): Option[Map[Address, Account]] = {
    for {
      sender <- tx.sender
      recipientAcc <- state.get(tx.recipient).orElse(Some(Account.empty))
      senderAcc <- state.get(sender)
      if senderAcc.balance >= tx.txFee + tx.amount && senderAcc.txNumber == tx.txNumber
    } yield {
      state
        .updated(tx.recipient, recipientAcc.add(tx.amount))
        .updated(sender, senderAcc.subtract(tx.amount + tx.txFee))
    }
  }

  def isValid(block: MinedBlock, state: State): Boolean = {
    val diffValid = state.getBlocks.headOption.exists { latestBlock =>
      latestBlock.hash != block.parentHash ||
        latestBlock.totalDifficulty + block.blockDifficulty == block.totalDifficulty
    }
    val txLimit = block.transactions.size <= maxTransactionsPerBlock
    MinerPoW.isValidPoW(block) && diffValid && txLimit
  }

  //block preparation
  def prepareBlock(
    state: State,
    targetDifficulty: BigInt): Option[UnminedBlock] = {
    state match {
      case State((parent, accounts) :: chain, txPool, Some(miner), _) =>
        Some(
          UnminedBlock(
            blockNumber = parent.blockNumber + 1,
            parentHash = parent.hash,
            transactions = selectTransactions(accounts, txPool),
            miner = miner,
            blockDifficulty = targetDifficulty,
            totalDifficulty = parent.totalDifficulty + targetDifficulty))
      case _ =>
        None
    }
  }

  private def selectTransactions(
    accounts: Map[Address, Account],
    txPool: List[SignedTransaction]): List[SignedTransaction] = {
    txPool
      .filter { tx =>
        tx.sender.exists { sender =>
          accounts.get(sender).exists { acc =>
            acc.txNumber == tx.txNumber && tx.amount + tx.txFee < acc.balance
          }
        }
      }
      .sortBy(tx => tx.txFee)
      .take(maxTransactionsPerBlock)
      //to make things simpler allow only 1 transaction per one sender
      .groupBy(_.sender)
      .flatMap { case (_, txs) => txs.headOption }
      .toList
  }

  //utils
  def getParent(block: MinedBlock, state: State): Option[MinedBlock] = {
    state.chain
      .find { case (chainBlock, _) => chainBlock.hash == block.parentHash }
      .map { case (chainBlock, _) => chainBlock }
  }

  @tailrec
  def isValid(branch: List[MinedBlock]): Boolean =
    branch match {
      case b1 :: b2 :: rest =>
        b1.totalDifficulty - b1.blockDifficulty == b2.totalDifficulty && b1.parentHash == b2.hash && isValid(
          rest)
      case _ :: Nil =>
        true
      case Nil =>
        true
    }

  //todo state of single node
  case class State(
      chain: List[(MinedBlock, Map[Address, Account])],
      txPool: List[SignedTransaction],
      minerAddress: Option[Address],
      nodes: List[ActorRef]) {

    //todo how to do transaction rollback
    def rollBack(hash: ByteString): State = {
      val (blocksToDiscard, commonPrefix) = chain.span {
        case (block, _) => block.hash != hash
      }
      val transactionToAdd = blocksToDiscard.flatMap {
        case (block, _) => block.transactions
      }

      copy(chain = commonPrefix, txPool = txPool ++ transactionToAdd)
    }

    def latestBlock(): Option[MinedBlock] = {
      getBlocks.headOption
    }

    def getBlocks: List[MinedBlock] = chain.map { case (block, _) => block }

    def getLatestBalance(address: Address): Option[BigInt] =
      getLatestAccounts.get(address).map(_.balance)

    def getLatestAccounts: Map[Address, Account] = {
      getAccounts.headOption.getOrElse(Map.empty)
    }

    def getAccounts: List[Map[Address, Account]] =
      chain.map { case (_, accounts) => accounts }

  }
}
