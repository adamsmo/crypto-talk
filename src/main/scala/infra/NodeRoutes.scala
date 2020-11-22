package infra

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.marshallers.sprayjson._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import infra.Supervisor.GetClusterMembers
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future
import scala.concurrent.duration._

trait NodeRoutes extends SprayJsonSupport with DefaultJsonProtocol {

  implicit def system: ActorSystem

  def supervisor: ActorRef

  implicit lazy val timeout = Timeout(5.seconds)

  lazy val healthRoute: Route = pathPrefix("health") {
    concat(pathEnd {
      concat(get {
        complete(StatusCodes.OK)
      })
    })
  }

  lazy val statusRoutes: Route = pathPrefix("status") {
    concat(pathPrefix("members") {
      concat(pathEnd {
        concat(get {
          val membersFuture: Future[List[String]] =
            (supervisor ? GetClusterMembers).mapTo[List[String]]
          onSuccess(membersFuture) { members =>
            complete(StatusCodes.OK, members)
          }
        })
      })
    })
  }
}
