package actors

import akka.actor.{Actor, ActorLogging}
import akka.util.ByteString
import domain.{Address, Block}

class CoinNode extends Actor with ActorLogging {
  override def receive: Receive = {
    case m => log.debug(s"$m")
  }

  private def handleOperation(): Unit = {

  }
}

case object CoinNode {

  val blokchain: List[Block] = List(Block(ByteString("00" * 32), Nil, Address(), ByteString("42"), ByteString("42")))

}