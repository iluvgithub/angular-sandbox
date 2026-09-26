package com.myway.angular.sandbox.services.uppercase

import cats.effect.Concurrent
import cats.syntax.all._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

object UpperCaseService {

  final case class UppercaseRequest(text: String)
  private final case class UppercaseResponse(result: String)

  implicit def uppercaseRequestDecoder[F[_]: Concurrent]: EntityDecoder[F, UppercaseRequest] =
    jsonOf[F, UppercaseRequest]

  def respond[F[_]: Concurrent](req: Request[F]): F[Response[F]] = {
    val dsl = Http4sDsl[F]
    import dsl._

    for {
      body <- req.as[UppercaseRequest]
      input  = body.text
      output = callService(input)
      resp <- Ok(UppercaseResponse(output).asJson)
    } yield resp
  }

  private def callService(input: String): String = input.toUpperCase
}
