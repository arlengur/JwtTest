package ru.arlen.jwt

import ru.arlen.jwt.UserRepository.UserNotFound

import scala.concurrent.Future

trait UserRepository {
  def findUser(name: String, pass: String): Future[User]
}

object UserRepository {
  final case class UserNotFound() extends Exception(s"User with such name or password not found.")
}

class InMemoryUserRepository(initialTodos: Seq[User] = Seq.empty) extends UserRepository {
  private val users: Vector[User] = initialTodos.toVector

  override def findUser(name: String, pass: String): Future[User] =
    users.find(u=> u.name == name && u.password == pass) match {
      case Some(user) => Future.successful(user)
      case None => Future.failed(UserNotFound())
    }
}