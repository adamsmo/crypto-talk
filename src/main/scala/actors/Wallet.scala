package actors

import actors.CoinLogic.State
import actors.CoinNode.GetState
import actors.Wallet.SendCoins
import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.pattern.ask
import akka.util.Timeout
import domain._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Success

//asks periodically for own balance
//accepts amounts and receiver of tx and prepares tx to send to node
class Wallet(prv: PrvKey, pub: PubKey, node: ActorRef) extends Actor with ActorLogging {

  implicit val ctx: ExecutionContext = context.system.dispatcher
  implicit val askTimeOut: Timeout = 5.seconds
  private val address: Address = Address(pub)

  override def receive: Receive = {
    case SendCoins(amount, fee, recipient) =>
      //this should be call for specific account
      (node ? GetState).mapTo[State].onComplete {
        case Success(state) =>
          state.getLatestAccounts.get(address).foreach { acc =>
            val tx = UnsignedTransaction(amount, fee, recipient, acc.txNumber)
          }
        case _ =>
          log.error("fail to get accounts")
      }
  }
}

object Wallet {
  def props(prv: PrvKey, pub: PubKey, node: ActorRef) = Props(new Wallet(prv, pub, node))

  case class SendCoins(amount: BigInt, fee: BigInt, recipient: Address)
}
