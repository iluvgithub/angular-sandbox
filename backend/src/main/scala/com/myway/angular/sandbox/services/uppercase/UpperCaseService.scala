package com.myway.angular.sandbox.services.uppercase

import cats.effect.IO
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.{EntityDecoder, _}

object UpperCaseService {

  final case class UppercaseRequest(text: String)
  final case class UppercaseResponse(result: String)

  implicit val uppercaseRequestDecoder: EntityDecoder[IO, UppercaseRequest] =
    jsonOf[IO, UppercaseRequest]

  def respond(req: Request[IO]): IO[Response[IO]] = for {
    body <- req.as[UppercaseRequest]
    resp <- Ok(UppercaseResponse(body.text.toUpperCase).asJson)
  } yield resp

}
