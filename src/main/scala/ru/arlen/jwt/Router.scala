package ru.arlen.jwt

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.{Directives, Route}
import io.circe.Json
import io.circe.parser._
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim, JwtTime}

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait Router {
  def route: Route
}

final case class LoginRequest(username: String, password: String)

class JwtRouter(users: UserRepository, secretKey: String, aTokenExp: Long, rTokenExp: Long) extends Router with Directives {
  // libraries for JSON encoding and decoding for our models
  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
  import io.circe.generic.auto._

  override def route: Route =
    pathEndOrSingleSlash {
      post {
        entity(as[LoginRequest]) {
          case LoginRequest(name, pass) => getTokensForUser(users.find(name, pass))
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
    } ~ path("auth") {
      post {
        optionalHeaderValueByName("Refresh-Token") {
          case Some(jwt) =>
            Jwt.decodeRaw(jwt, secretKey, Seq(JwtAlgorithm.HS256)) match {
              case Success(claims) =>
                val jsonObject = parse(claims).getOrElse(Json.Null)
                jsonObject.hcursor.get[Long]("iss") match {
                  case Right(id) =>
                    getTokensForUser(users.find(id, jwt))
                  case Left(error) =>
                    complete(StatusCodes.Unauthorized -> error.getMessage)
                }
              case Failure(error) =>
                complete(StatusCodes.Unauthorized -> error.getMessage)
            }
          case None => complete(StatusCodes.Forbidden)
        }
      }
    }

  private def getTokensForUser[T <: User](f: Future[T]): Route = {
    onComplete(f) {
      case Success(user) =>
        val accessToken = Jwt.encode(JwtClaim().issuedNow.expiresIn(aTokenExp), secretKey, JwtAlgorithm.HS256)
        val refreshToken = Jwt.encode(JwtClaim().by(user.id.toString).issuedNow.expiresIn(rTokenExp), secretKey, JwtAlgorithm.HS256)
        onComplete(users.update(user.id, refreshToken)) {
          case Success(_) =>
            respondWithHeaders(
              RawHeader("Access-Token", accessToken),
              RawHeader("Refresh-Token", refreshToken),
              RawHeader("Expires-In", (JwtTime.nowSeconds + rTokenExp).toString)) {
              complete(StatusCodes.OK)
            }
          case Failure(error) => complete(StatusCodes.InternalServerError -> error.getMessage)
        }
      case Failure(error) => complete(StatusCodes.NotFound -> error.getMessage)
    }
  }
}
