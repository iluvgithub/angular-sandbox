package com.myway.angular.sandbox.main

import cats.effect.IO
import com.myway.angular.sandbox.service.clock.ClockService
import com.myway.angular.sandbox.service.gridpoll.GridService
import com.myway.angular.sandbox.service.uppercase.UppercaseService
import fs2.Stream
import io.circe.syntax.EncoderOps
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, ServerSentEvent}
import com.myway.angular.sandbox.service.gridpoll.GridSnapshot._

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.semigroupk._
import com.comcast.ip4s._
import com.myway.angular.sandbox.service.clock.ClockService
import com.myway.angular.sandbox.service.gridpoll.GridService
import com.myway.angular.sandbox.service.uppercase.UppercaseService
import fs2.Stream
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._

import org.http4s.server.staticcontent.resourceServiceBuilder
import org.http4s.{HttpRoutes, ServerSentEvent, StaticFile}

object Routes {

  def makeApiRoutes(gridService: GridService): HttpRoutes[IO] = HttpRoutes.of[IO] {
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
}
