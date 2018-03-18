package actors

import akka.actor.{ Actor, ActorRef, Props }
import domain.{ PrvKey, PubKey }

//asks periodically for own balance
//accepts amounts and receiver of tx and prepares tx to send to node
class Wallet(prv: PrvKey, pub: PubKey, node: ActorRef) extends Actor {
  override def receive: Receive = ???
}

object Wallet {
  def props(prv: PrvKey, pub: PubKey, node: ActorRef) = Props(new Wallet(prv, pub, node))
}
