import actors.Discovery
import akka.actor.ActorSystem

object Main extends App {
  val actorSystem = ActorSystem("coin-system")

  actorSystem.actorOf(Discovery.prosp())
}
