package com.myway.angular.sandbox.main

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.semigroupk.*
import com.comcast.ip4s.*
import com.myway.angular.sandbox.service.clock.ClockService
import com.myway.angular.sandbox.service.gridpoll.GridService
import com.myway.angular.sandbox.service.uppercase.UppercaseService
import fs2.Stream
import io.circe.syntax.*
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.middleware.{CORS, Logger as Http4sLogger}
import org.http4s.server.staticcontent.resourceServiceBuilder
import org.http4s.{HttpRoutes, ServerSentEvent, StaticFile}

object Main extends IOApp {

  // The Angular build is copied onto the classpath at build time under webapp/
  // (see the frontend-maven-plugin + maven-resources-plugin config in pom.xml).
  private val webappBasePath = "/webapp"

  private def apiRoutes(gridService: GridService): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "uppercase" / text =>
      for {
        up <- UppercaseService.toUppercase(text)
        ok <- Ok(up)
      } yield ok

    case GET -> Root / "callclock" =>
      for {
        up <- ClockService.clockNow
        ok <- Ok(up)
      } yield ok

    case GET -> Root / "api" / "grid" / "state" =>
      for {
        snap <- gridService.snapshot
        ok   <- Ok(snap.asJson)
      } yield ok

    // Live updates: one Server-Sent Event per cell change.
    case GET -> Root / "api" / "grid" / "stream" =>
      val events: Stream[IO, ServerSentEvent] =
        gridService.updates.map(u => ServerSentEvent(data = Some(u.asJson.noSpaces)))

      Ok(events)
  }
  // Serves the SPA's own entry point at the root path.
  private val indexRoute: HttpRoutes[IO] = HttpRoutes.of[IO] { case req @ GET -> Root =>
    StaticFile
      .fromResource[IO](s"$webappBasePath/index.html", Some(req))
      .getOrElseF(NotFound())
  }
  private val spaFallbackRoute: HttpRoutes[IO] = HttpRoutes.of[IO] { case req @ GET -> _ =>
    StaticFile
      .fromResource[IO](s"$webappBasePath/index.html", Some(req))
      .getOrElseF(NotFound())
  }

  // Serves every other file the Angular build produced (JS bundles, CSS, favicon, ...).
  private val staticAssetRoutes: HttpRoutes[IO] =
    resourceServiceBuilder[IO](webappBasePath).toRoutes

  private def makeAllRoutes(gridService: GridService): HttpRoutes[IO] =
    apiRoutes(gridService) <+> staticAssetRoutes <+> indexRoute

  private def makeHttpApp(corsRoutes: HttpRoutes[IO]) =
    Http4sLogger.httpApp[IO](logHeaders = true, logBody = false)(corsRoutes.orNotFound)

  // Render (and most PaaS hosts) inject a PORT env var and require the app to bind to it.
  private def resolvePort: Port =
    sys.env.get("PORT").flatMap(Port.fromString).getOrElse(port"8080")

  override def run(args: List[String]): IO[ExitCode] =
    for {
      gridService <- GridService.createDefaultGrid
      _           <- gridService.run.compile.drain.start
      allRoutes  = makeAllRoutes(gridService)
      corsRoutes = CORS.policy.withAllowOriginAll(allRoutes)
      exitCode <- EmberServerBuilder
        .default[IO]
        .withHost(host"0.0.0.0")
        .withPort(resolvePort)
        .withHttpApp(makeHttpApp(corsRoutes))
        .build
        .use { server =>
          IO.println(s"sandbox angular app listening on ${server.address}") *> IO.never
        }
        .as(ExitCode.Success)
    } yield exitCode
}
