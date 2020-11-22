package infra

import actors.Node.{ConnectNode, DisconnectNode}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._

object ClusterListener {
  def props(nodeId: String, cluster: Cluster, node: ActorRef) =
    Props(new ClusterListener(nodeId, cluster, node))
}

class ClusterListener(nodeId: String, cluster: Cluster, node: ActorRef)
  extends Actor
  with ActorLogging {

  override def preStart(): Unit = {
    cluster.subscribe(
      self,
      initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent],
      classOf[UnreachableMember])
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) =>
      log.info("Node {} - Member is Up: {}", nodeId, member.address)
      if (!member.address.host.contains(nodeId)) {
        node ! ConnectNode(member.address)
      }
    case UnreachableMember(member) =>
      log.info(s"Node {} - Member detected as unreachable: {}", nodeId, member)
    case MemberRemoved(member, previousStatus) =>
      log.info(
        s"Node {} - Member is Removed: {} after {}",
        nodeId,
        member.address,
        previousStatus)
      node ! DisconnectNode(member.address)
    case _: MemberEvent => // ignore
  }
}
