package com.myway.angular.sandbox.main

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.semigroupk._
import com.comcast.ip4s._
import com.myway.angular.sandbox.service.clock.ClockService
import com.myway.angular.sandbox.service.uppercase.UppercaseService
import fs2.Stream
import org.http4s.{HttpRoutes, StaticFile}
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.Logger
import org.http4s.server.staticcontent.resourceServiceBuilder
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame

import java.time.LocalDateTime

object Main extends IOApp {

  // The Angular build is copied onto the classpath at build time under webapp/
  // (see the frontend-maven-plugin + maven-resources-plugin config in pom.xml).
  private val webappBasePath = "/webapp"

  private val apiRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "uppercase" / text =>
      for {
        up <- UppercaseService.toUppercase(text)
        ok <- Ok(up)
      } yield ok

    case GET -> Root / "clock" / text =>
      for {
        up <- UppercaseService.toUppercase(text)
        ok <- Ok(up)
      } yield ok
  }

  // Serves the SPA's own entry point at the root path.
  private val indexRoute: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req@GET -> Root =>
      StaticFile
        .fromResource[IO](s"$webappBasePath/index.html", Some(req))
        .getOrElseF(NotFound())
  }
  private val spaFallbackRoute: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req@GET -> _ =>
      StaticFile
        .fromResource[IO](s"$webappBasePath/index.html", Some(req))
        .getOrElseF(NotFound())
  }


  // GET /ws/clock  ->  WebSocket that pushes the current time once a second,
  // formatted as yyyy:MM:dd HH:mm:ss. The connection just streams; anything
  // the client sends back is ignored.
  private def clockRoute(wsb: WebSocketBuilder2[IO]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "ws" / "clock" =>
      ClockService.clock(wsb) 
  }

  // Serves every other file the Angular build produced (JS bundles, CSS, favicon, ...).
  private val staticAssetRoutes: HttpRoutes[IO] =
    resourceServiceBuilder[IO](webappBasePath).toRoutes

  private val allRoutes: HttpRoutes[IO] = apiRoutes <+> indexRoute <+> staticAssetRoutes

  private val httpApp = Logger.httpApp(logHeaders = true, logBody = false)(allRoutes.orNotFound)

  // Render (and most PaaS hosts) inject a PORT env var and require the app to bind to it.
  private def resolvePort: Port =
    sys.env.get("PORT").flatMap(Port.fromString).getOrElse(port"8080")

  override def run(args: List[String]): IO[ExitCode] =

    EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(resolvePort)
      .withHttpWebSocketApp { wsb =>
        val allRoutes = apiRoutes <+> clockRoute(wsb) <+> staticAssetRoutes <+> spaFallbackRoute
        Logger.httpApp(logHeaders = true, logBody = false)(allRoutes.orNotFound)
      }
      .build
      .use { server =>
        IO.println(s"uppercase-app listening on ${server.address}") *> IO.never
      }
      .as(ExitCode.Success)
}