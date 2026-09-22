package com.myway.angular.sandbox.main

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.toSemigroupKOps
import com.comcast.ip4s._
import com.myway.angular.sandbox.service.clock.ClockService
import com.myway.angular.sandbox.service.gridpoll.GridService
import com.myway.angular.sandbox.service.uppercase.UppercaseService
import fs2.Stream
import io.circe.syntax.EncoderOps
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.{CORS, Logger}
import org.http4s.server.staticcontent.resourceServiceBuilder
import org.http4s.{HttpApp, HttpRoutes, ServerSentEvent, StaticFile}

object Main extends IOApp {

  // The Angular build is copied onto the classpath at build time under webapp/
  // (see the frontend-maven-plugin + maven-resources-plugin config in pom.xml).
  private val webappBasePath = "/webapp"

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
    Routes.makeApiRoutes(gridService) <+> staticAssetRoutes <+> indexRoute <+> spaFallbackRoute

  private def makeHttpApp(allRoutes: HttpRoutes[IO]): HttpApp[IO] =
    Logger.httpApp(logHeaders = true, logBody = false)(allRoutes.orNotFound)

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
