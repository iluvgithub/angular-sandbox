package com.myway.angular.sandbox.server

import cats.effect._
import cats.syntax.semigroupk._
import io.circe.generic.auto._
import io.circe.syntax._
import fs2.Stream
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.http4s.server.middleware.CORS
import org.http4s.server.staticcontent.resourceServiceBuilder

import scala.concurrent.duration._
import scala.util.Random

object Server {

  // Grid dimensions: 3 rows (i: 0..2) x 4 columns (j: 0..3)
  val Rows = 5
  val Cols = 4

  final case class Cell(i: Int, j: Int, value: Double)

  final case class UppercaseRequest(text: String)
  final case class UppercaseResponse(result: String)

  implicit val uppercaseRequestDecoder: EntityDecoder[IO, UppercaseRequest] =
    jsonOf[IO, UppercaseRequest]

  /** An infinite stream that emits a randomly chosen cell (i, j) with a
    * random value roughly every 800ms, encoded as a Server-Sent Event.
    */
  def randomCellStream: Stream[IO, ServerSentEvent] =
    Stream
      .awakeEvery[IO](800.millis)
      .evalMap { _ =>
        IO {
          val i = Random.nextInt(Rows)
          val j = Random.nextInt(Cols)
          val value = Math.round(Random.nextDouble() * 10000) / 100.0 // 0.00 - 100.00
          Cell(i, j, value)
        }
      }
      .map(cell => ServerSentEvent(data = Some(cell.asJson.noSpaces)))

  val apiRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "api" / "stream" =>
      Ok(randomCellStream)

    case GET -> Root / "api" / "health" =>
      Ok(Map("status" -> "ok").asJson)
        .map(_.withContentType(`Content-Type`(MediaType.application.json)))

    case req @ POST -> Root / "api" / "uppercase" =>
      for {
        body <- req.as[UppercaseRequest]
        resp <- Ok(UppercaseResponse(body.text.toUpperCase).asJson)
      } yield resp
  }

  val corsApiRoutes: HttpRoutes[IO] =
    CORS.policy.withAllowOriginAll.withAllowCredentials(false).apply(apiRoutes)

  /** Serves the compiled Angular assets from src/main/resources/static
    * (classpath resource "/static"), which is populated at build time
    * by copying frontend/dist/frontend into this module's resources.
    */
  val staticAssetRoutes: HttpRoutes[IO] =
    resourceServiceBuilder[IO]("/static").toRoutes

  /** Fallback: serve index.html for any other GET request, so Angular's
    * client-side router works on deep links / page refreshes.
    */
  val indexFallbackRoute: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> _ =>
      StaticFile.fromResource[IO]("/static/index.html", Some(req)).getOrElseF(NotFound())
  }

    val routes: HttpRoutes[IO] =
    corsApiRoutes <+> staticAssetRoutes <+> indexFallbackRoute
}
