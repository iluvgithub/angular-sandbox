package com.myway.angular.sandbox.main
import cats.effect._
import com.comcast.ip4s._
import com.myway.angular.sandbox.server.Routes
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.Logger

object Main extends IOApp.Simple {

  private val port: Port =
    Port.fromString(sys.env.getOrElse("PORT", "8080")).getOrElse(port"8080")

  val run: IO[Unit] =
    EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(port)
      .withHttpApp(Logger.httpApp(logHeaders = false, logBody = false)(Routes.routes.orNotFound))
      .build
      .useForever
}
