package ru.arlen.jwt

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn
import scala.util.{Failure, Success}

object Boot {
  def main(args: Array[String]) {
    implicit val system: ActorSystem = ActorSystem(name = "jwt-system")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    val config = system.settings.config
    val host = config.getString("default-conf.host")
    val port = config.getInt("default-conf.port")
    val secretKey = config.getString("default-conf.secretKey")
    val aTokenExpSec = config.getLong("default-conf.accessTokenExpSec")
    val rTokenExpSec = config.getLong("default-conf.refreshTokenExpSec")

    val userRepository = new InMemoryUserRepository(Seq(
      User(1, "Bob", "pass1"),
      User(2, "John", "pass2"),
      User(3, "Mike", "pass3")
    ))

    val router = new JwtRouter(userRepository, secretKey, aTokenExpSec, rTokenExpSec)
    val server = Http().bindAndHandle(router.route, host, port)

    println(s"Server successfully bound at http://$host:$port\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return

   server
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete {
      case Success(_) =>
        println("Successfully finished!")
        system.terminate()
      case Failure(error) =>
        println(s"Failed: ${error.getMessage}")
    } // and shutdown when done
  }
}
