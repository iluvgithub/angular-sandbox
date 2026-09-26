package com.myway.angular.sandbox.server

import cats.effect._
import cats.syntax.semigroupk._
import com.myway.angular.sandbox.services.chat.ChatService
import com.myway.angular.sandbox.services.grid.RandomValueGridService
import com.myway.angular.sandbox.services.grid.RandomValueGridServiceInstance.{
  ColsParam,
  RowsParam
}
import com.myway.angular.sandbox.services.uppercase.UpperCaseService
import fs2.concurrent.Topic
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.http4s.server.middleware.CORS
import org.http4s.server.staticcontent.resourceServiceBuilder

case class Routes(
    upperCaseService: UpperCaseService,
    randomValueGridService: RandomValueGridService,
    chatService: ChatService
) {

  def apiRoutes(topicsMap: Map[String, Topic[IO, String]]): HttpRoutes[IO] =
    HttpRoutes.of[IO] {

      case GET -> Root / "api" / "stream" :? RowsParam(rowsParam) +& ColsParam(
            colsParam
          ) =>
        randomValueGridService.respond(rowsParam, colsParam)

      case req @ POST -> Root / "api" / "uppercase" =>
        upperCaseService.respond(req)

      case GET -> Root / "api" / "health" =>
        Ok(Map("status" -> "ok").asJson)
          .map(_.withContentType(`Content-Type`(MediaType.application.json)))

      case GET -> Root / "api" / "chat" / room / "stream" =>
        chatService.subscribe(topicsMap, room)

      // POST /api/chat/send  {"room": "Asterix", "text": "..."} - publish to that room
      case req @ POST -> Root / "api" / "chat" / "send" =>
        chatService.sendMessage(topicsMap, req)

    }

  private def corsApiRoutes(
      topicsMap: Map[String, Topic[IO, String]]
  ): HttpRoutes[IO] =
    CORS.policy.withAllowOriginAll
      .withAllowCredentials(false)
      .apply(apiRoutes(topicsMap))

  /** Serves the compiled Angular assets from src/main/resources/static
    * (classpath resource "/static"), which is populated at build time by
    * copying frontend/dist/frontend into this module's resources.
    */
  private val staticAssetRoutes: HttpRoutes[IO] =
    resourceServiceBuilder[IO]("/static").toRoutes

  /** Fallback: serve index.html for any other GET request, so Angular's
    * client-side router works on deep links / page refreshes.
    */
  private val indexFallbackRoute: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> _ =>
      StaticFile
        .fromResource[IO]("/static/index.html", Some(req))
        .getOrElseF(NotFound())
  }

  def routes(topicsMap: Map[String, Topic[IO, String]]): HttpRoutes[IO] =
    corsApiRoutes(topicsMap) <+> staticAssetRoutes <+> indexFallbackRoute
}
