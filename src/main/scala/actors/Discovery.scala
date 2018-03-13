package actors

import actors.Discovery.{GetAll, RegisterNew, RegisteredNodes}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}

class Discovery extends Actor with ActorLogging {

  override def receive: Receive = discoveryHandler(Set.empty)

  private def discoveryHandler(registeredNodes: Set[ActorRef]): Receive = {
    case RegisterNew(node) =>
      context.become(discoveryHandler(registeredNodes + node))
    case GetAll =>
      sender() ! RegisteredNodes(registeredNodes)
  }
}

object Discovery {

  case class RegisterNew(node: ActorRef)

  case object GetAll

  case class RegisteredNodes(nodes: Set[ActorRef])

  def props() = Props(new Discovery())
}