package infra

import actors.Node.NodeParams
import actors.{Node, Wallet}
import akka.actor.{Actor, ActorRef, Props}
import crypto.ECDSA
import domain.Address
import infra.ClusterManager.GetMembers
import infra.Supervisor.GetClusterMembers

import scala.concurrent.duration._

class Supervisor(nodeId: String, isMining: Boolean) extends Actor {

  val (prv, pub) = ECDSA.generateKeyPair()
  val recipientAddress: Address = Address(pub)

  val standardParams: NodeParams = NodeParams(
    sendBlocks = true,
    sendTransactions = true,
    isMining = isMining,
    miner = recipientAddress,
    miningInterval = 5.seconds,
    miningDifficulty = 6,
    miningDifficultyDeviation = 2)

  val node: ActorRef =
    context.actorOf(Node.props(standardParams), s"node")

  val clusterManager: ActorRef =
    context.actorOf(ClusterManager.props(nodeId, node), "clusterManager")

  val wallet: ActorRef =
    context.actorOf(Wallet.props(prv, pub, s"wallet-$nodeId", node), s"wallet")

  override def receive: Receive = {
    case GetClusterMembers => clusterManager forward GetMembers
  }
}

object Supervisor {
  sealed trait SupervisorMessage

  case object GetClusterMembers

  def props(nodeId: String, isMining: Boolean) =
    Props(new Supervisor(nodeId, isMining))
}
