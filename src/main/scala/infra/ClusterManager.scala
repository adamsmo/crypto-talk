package infra

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.cluster.{ Cluster, MemberStatus }
import infra.ClusterManager.GetMembers

object ClusterManager {

  sealed trait ClusterMessage
  case object GetMembers extends ClusterMessage

  def props(nodeId: String, node: ActorRef) =
    Props(new ClusterManager(nodeId, node))
}

class ClusterManager(nodeId: String, node: ActorRef)
  extends Actor
  with ActorLogging {

  val cluster: Cluster = Cluster(context.system)
  val listener: ActorRef =
    context.actorOf(
      ClusterListener.props(nodeId, cluster, node),
      "clusterListener")

  override def receive: Receive = {
    case GetMembers =>
      sender() ! cluster.state.members
        .filter(_.status == MemberStatus.up)
        .map(_.address.toString)
        .toList
  }
}
