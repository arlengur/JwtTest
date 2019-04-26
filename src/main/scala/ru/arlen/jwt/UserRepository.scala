package ru.arlen.jwt

import ru.arlen.jwt.UserRepository.UserNotFound

import scala.concurrent.Future

trait UserRepository {
  def find(name: String, pass: String): Future[User]

  def find(id: Long, refreshToken: String): Future[User]

  def update(id: Long, refreshToken: String): Future[User]
}

object UserRepository {

  final case class UserNotFound() extends Exception(s"User with such name or password not found.")

}

class InMemoryUserRepository(initialUsers: Seq[User] = Seq.empty) extends UserRepository {
  private var users: Vector[User] = initialUsers.toVector

  override def find(name: String, pass: String): Future[User] =
    users.find(u => u.name == name && u.password == pass) match {
      case Some(user) => Future.successful(user)
      case None => Future.failed(UserNotFound())
    }

  override def find(id: Long, refreshToken: String): Future[User] =
    users.find(u => u.id == id && u.refreshToken.getOrElse("") == refreshToken) match {
      case Some(user) => Future.successful(user)
      case None => Future.failed(UserNotFound())
    }

  override def update(id: Long, refreshToken: String): Future[User] = users.find(_.id == id) match {
    case Some(user) =>
      val newUser = user.copy(refreshToken = Some(refreshToken))
      users = users.map(u => if (u.id == id) newUser else u)
      Future.successful(newUser)
    case None => Future.failed(UserNotFound())
  }

  override def toString = s"InMemoryUserRepository:\n${users.mkString("\n")}"
}