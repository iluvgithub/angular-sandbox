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


  val Period: FiniteDuration = 800.millis

  final case class Cell(i: Int, j: Int, value: Double)

  final case class UppercaseRequest(text: String)
  final case class UppercaseResponse(result: String)

  implicit val uppercaseRequestDecoder: EntityDecoder[IO, UppercaseRequest] =
    jsonOf[IO, UppercaseRequest]

  /** An infinite stream that emits a randomly chosen cell (i, j) with a
    * random value roughly every 800ms, encoded as a Server-Sent Event.
    */
  def randomCellStream(rows: Int, cols: Int): Stream[IO, ServerSentEvent] =
    Stream
      .awakeEvery[IO](Period)
      .evalMap { _ =>
        IO {
          val i = Random.nextInt(rows)
          val j = Random.nextInt(cols)
          val value = Math.round(Random.nextDouble() * 10000) / 100.0 // 0.00 - 100.00
          Cell(i, j, value)
        }
      }
      .map(cell => ServerSentEvent(data = Some(cell.asJson.noSpaces)))
  object RowsParam extends OptionalQueryParamDecoderMatcher[Int]("rows")
  object ColsParam extends OptionalQueryParamDecoderMatcher[Int]("cols")


  private val MinDim = 1
  private val MaxDim = 50

  private def clampDim(value: Option[Int], default: Int): Int =
    value.filter(v => v >= MinDim && v <= MaxDim).getOrElse(default)



  val apiRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    case GET -> Root / "api" / "stream" :? RowsParam(rowsParam) +& ColsParam(colsParam) =>
      val rows = clampDim(rowsParam, 5)
      val cols = clampDim(colsParam, 3)
      Ok(randomCellStream(rows, cols))

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
