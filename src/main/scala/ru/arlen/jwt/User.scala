package ru.arlen.jwt

case class User(id: Long, name: String, password: String, refreshToken: Option[String] = None)
case class UpdateToken(refreshToken: String)
