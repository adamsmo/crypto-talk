package actors

import actors.CoinNode.State
import domain._

object CoinLogic {
  //block execution
  def executeBlock(block: MinedBlock, state: State): Option[State] = {
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

  //block preparation
  def prepareBlock(state: State, targetDifficulty: BigInt): Option[UnminedBlock] = {
    state match {
      case State((parent, accounts) :: chain, txPool, Some(miner)) =>
        Some(UnminedBlock(
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

  private def selectTransactions(accounts: Map[Address, Account], txPool: List[Transaction]): List[Transaction] = {
    txPool
      .filter { tx =>
        tx.sender.exists { sender =>
          accounts.get(sender).exists { acc =>
            acc.txNumber == tx.txNumber && tx.amount + tx.txFee < acc.balance
          }
        }
      }
      .sortBy(tx => tx.txFee)
      .take(CoinNode.maxTransactionsPerBlock)
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

  def isValid(block: MinedBlock, state: State): Boolean = {
    val diffValid = state.chain.headOption.exists {
      case (latestBlock, _) =>
        latestBlock.totalDifficulty + block.blockDifficulty == block.totalDifficulty
    }
    val txLimit = block.transactions.size <= CoinNode.maxTransactionsPerBlock
    MinerPoW.isValidPoW(block) && diffValid && txLimit
  }

  def isValid(branch: List[MinedBlock]): Boolean = branch match {
    case b1 :: b2 :: rest =>
      b1.totalDifficulty - b1.blockDifficulty == b2.totalDifficulty && b1.parentHash == b2.hash && isValid(rest)
    case _ :: Nil =>
      true
    case Nil =>
      true
  }
}
