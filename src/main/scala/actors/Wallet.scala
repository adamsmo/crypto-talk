package actors

import actors.Logic.State
import actors.Node.GetState
import actors.Wallet.{ Balance, CheckBalance, SendCoins, UpdateBalance }
import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.pattern.ask
import akka.util.Timeout
import domain._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Success

//asks periodically for own balance
//accepts amounts and receiver of tx and prepares tx to send to node
class Wallet(prv: PrvKey, pub: PubKey, name: String, node: ActorRef)
  extends Actor
  with ActorLogging {

  implicit val ctx: ExecutionContext = context.system.dispatcher
  implicit val askTimeOut: Timeout = 5.seconds
  private val address: Address = Address(pub)

  override def preStart(): Unit = {
    //todo 1.2 ticker to check balance every 3s to sync it with connected node state
    context.system.scheduler.schedule(0.seconds, 3.second, self, UpdateBalance)
  }

  override def receive: Receive = receive(None)

  private def receive(balance: Option[BigInt]): Receive = {
    //todo 1.1 sign and send tx to the node
    case SendCoins(amount, fee, recipient) =>
      //this should be call for specific account
      (node ? GetState).mapTo[State].onComplete {
        case Success(state) =>
          state.getLatestAccounts.get(address).foreach { acc =>
            val tx = UnsignedTransaction(amount, fee, recipient, acc.txNumber)
            val stx = SignedTransaction(tx, prv)
            node ! stx
          }
        case _ =>
          log.error("fail to send transaction")
      }

    //todo 1.3 synchronize balance with node
    case UpdateBalance =>
      (node ? GetState).mapTo[State].foreach { state =>
        val newBalance = state.getLatestBalance(address)
        log.info(
          newBalance
            .map(balance => s"$name has: $balance coins")
            .getOrElse(s"$name does not exists yet"))

        context.become(receive(newBalance))
      }

    //todo 1.4 handle query for balance
    case CheckBalance =>
      sender() ! Balance(balance.getOrElse(0))

  }
}

object Wallet {
  def props(prv: PrvKey, pub: PubKey, name: String, node: ActorRef): Props =
    Props(new Wallet(prv, pub, name, node))

  case class SendCoins(amount: BigInt, fee: BigInt, recipient: Address)

  case class Balance(b: BigInt)

  case object UpdateBalance

  case object CheckBalance
}
