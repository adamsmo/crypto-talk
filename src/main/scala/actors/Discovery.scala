package actors

import akka.actor.{Actor, ActorLogging, Props}

class Discovery extends Actor with ActorLogging {

  override def receive: Receive = {
    case m => log.debug(s"got message $m")
  }
}

object Discovery {
  def prosp() = Props(new Discovery())
}