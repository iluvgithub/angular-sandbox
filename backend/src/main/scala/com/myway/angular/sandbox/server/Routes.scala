package com.myway.angular.sandbox.server

import cats.effect._
import cats.syntax.semigroupk._
import com.myway.angular.sandbox.services.grid.RandomValueGridService
import com.myway.angular.sandbox.services.grid.RandomValueGridService.{ColsParam, RowsParam}
import com.myway.angular.sandbox.services.uppercase.UpperCaseService
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.http4s.server.middleware.CORS
import org.http4s.server.staticcontent.resourceServiceBuilder

object Routes {

  val apiRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    case GET -> Root / "api" / "stream" :? RowsParam(rowsParam) +& ColsParam(colsParam) =>
      RandomValueGridService.respond(rowsParam, colsParam)

    case req @ POST -> Root / "api" / "uppercase" => UpperCaseService.respond(req)

    case GET -> Root / "api" / "health" =>
      Ok(Map("status" -> "ok").asJson)
        .map(_.withContentType(`Content-Type`(MediaType.application.json)))
  }

  val corsApiRoutes: HttpRoutes[IO] =
    CORS.policy.withAllowOriginAll.withAllowCredentials(false).apply(apiRoutes)

  /** Serves the compiled Angular assets from src/main/resources/static (classpath resource
    * "/static"), which is populated at build time by copying frontend/dist/frontend into this
    * module's resources.
    */
  val staticAssetRoutes: HttpRoutes[IO] =
    resourceServiceBuilder[IO]("/static").toRoutes

  /** Fallback: serve index.html for any other GET request, so Angular's client-side router works on
    * deep links / page refreshes.
    */
  val indexFallbackRoute: HttpRoutes[IO] = HttpRoutes.of[IO] { case req @ GET -> _ =>
    StaticFile.fromResource[IO]("/static/index.html", Some(req)).getOrElseF(NotFound())
  }

  val routes: HttpRoutes[IO] =
    corsApiRoutes <+> staticAssetRoutes <+> indexFallbackRoute
}
