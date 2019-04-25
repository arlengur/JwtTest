package ru.arlen.jwt

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.{Directives, Route}
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

import scala.util.{Failure, Success}

trait Router {
  def route: Route
}

final case class LoginRequest(username: String, password: String)

class JwtRouter(users: UserRepository, secretKey: String, tokenExp: Long) extends Router with Directives {
  // libraries for JSON encoding and decoding for our models
  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
  import io.circe.generic.auto._

  override def route: Route =
    pathEndOrSingleSlash {
      post {
        entity(as[User]) {
          case User(name, pass) =>
            onComplete(users.findUser(name, pass)) {
              case Success(user) =>
                val token = Jwt.encode(JwtClaim().issuedNow.expiresIn(tokenExp), secretKey, JwtAlgorithm.HS256)
                respondWithHeader(RawHeader("Access-Token", token)) {
                  complete(StatusCodes.OK)
                }
              case Failure(error) => complete(StatusCodes.NotFound->error.getMessage)
            }
          case _ => complete(StatusCodes.Forbidden)
        }
      } ~ get {
        optionalHeaderValueByName("Authorization") {
          case Some(jwt) =>
            Jwt.decode(jwt, secretKey, Seq(JwtAlgorithm.HS256)) match {
              case Success(_) => complete(StatusCodes.OK)
              case Failure(error) => complete(StatusCodes.Unauthorized -> error.getMessage)
            }
          case None => complete(StatusCodes.Forbidden)
        }
      }
    } ~ path("users") {
      get {
        optionalHeaderValueByName("Authorization") {
          case Some(jwt) =>
            Jwt.decode(jwt, secretKey, Seq(JwtAlgorithm.HS256)) match {
              case Success(_) =>
                complete(StatusCodes.OK -> "User list")
              case Failure(error) => complete(StatusCodes.Unauthorized -> error.getMessage)
            }
          case None => complete(StatusCodes.Forbidden)
        }
      } ~ post {
        optionalHeaderValueByName("Authorization") {
          case Some(jwt) =>
            Jwt.decode(jwt, secretKey, Seq(JwtAlgorithm.HS256)) match {
              case Success(_) =>
                complete(StatusCodes.OK -> "<<User added>>")
              case Failure(error) =>
                complete(StatusCodes.Unauthorized -> error.getMessage)
            }
          case None => complete(StatusCodes.Forbidden)
        }
      }
    }
}
